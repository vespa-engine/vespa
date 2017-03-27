// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/memfilepersistence/device/devicemanager.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/util/exception.h>
#include <sys/errno.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>

namespace storage {

namespace memfile {

class DeviceManagerTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(DeviceManagerTest);
    CPPUNIT_TEST(testEventClass);
    CPPUNIT_TEST(testEventSending);
    CPPUNIT_TEST(testXml);
    CPPUNIT_TEST_SUITE_END();

public:
    void testEventClass();
    void testEventSending();
    void testXml();

    framework::defaultimplementation::FakeClock _clock;
};

CPPUNIT_TEST_SUITE_REGISTRATION(DeviceManagerTest);

void DeviceManagerTest::testEventClass()
{
    // Test that creation various IO events through common errno errors
    // generates understandable errors.
    {
        IOEvent e(IOEvent::createEventFromErrno(1, ENOTDIR, "/mypath"));
        CPPUNIT_ASSERT_EQUAL(
            std::string("IOEvent(PATH_FAILURE, Not a directory: /mypath, time 1)"),
            e.toString(true));
        CPPUNIT_ASSERT_EQUAL(Device::PATH_FAILURE, e.getState());
    }
    {
        IOEvent e(IOEvent::createEventFromErrno(2, EACCES, "/mypath"));
        CPPUNIT_ASSERT_EQUAL(
            std::string("IOEvent(NO_PERMISSION, Permission denied: /mypath, time 2)"),
            e.toString(true));
        CPPUNIT_ASSERT_EQUAL(Device::NO_PERMISSION, e.getState());
    }
    {
        IOEvent e(IOEvent::createEventFromErrno(3, EIO, "/mypath"));
        CPPUNIT_ASSERT_EQUAL(
            std::string("IOEvent(IO_FAILURE, Input/output error: /mypath, time 3)"),
            e.toString(true));
        CPPUNIT_ASSERT_EQUAL(Device::IO_FAILURE, e.getState());
    }
    {
        IOEvent e(
                IOEvent::createEventFromErrno(4, EBADF, "/mypath", VESPA_STRLOC));
        CPPUNIT_ASSERT_PREFIX(
            std::string("IOEvent(INTERNAL_FAILURE, Bad file descriptor: /mypath"
                        ", testEventClass in"),
            e.toString(true));
        CPPUNIT_ASSERT_EQUAL(Device::INTERNAL_FAILURE, e.getState());
    }
}

namespace {

    struct Listener : public IOEventListener {
        std::ostringstream ost;

        Listener() : ost() { ost << "\n"; }
        virtual ~Listener() {}

        virtual void handleDirectoryEvent(Directory& dir, const IOEvent& e) {
            ost << "Dir " << dir.getPath() << ": " << e.toString(true) << "\n";
        }
        virtual void handlePartitionEvent(Partition& part, const IOEvent& e) {
            ost << "Partition " << part.getMountPoint() << ": "
                << e.toString(true) << "\n";
        }
        virtual void handleDiskEvent(Disk& disk, const IOEvent& e) {
            ost << "Disk " << disk.getId() << ": " << e.toString(true) << "\n";
        }

    };

}

void DeviceManagerTest::testEventSending()
{
        // Test that adding events to directories in the manager actually sends
        // these events on to listeners.
    DeviceManager manager(DeviceMapper::UP(new SimpleDeviceMapper), _clock);
    Listener l;
    manager.addIOEventListener(l);
    Directory::SP dir(manager.getDirectory("/home/foo/var", 0));
        // IO failures are disk events. Will mark all partitions and
        // directories on that disk bad
    dir->addEvent(IOEvent::createEventFromErrno(1, EIO, "/home/foo/var/foo"));
    dir->addEvent(IOEvent::createEventFromErrno(2, EBADF, "/home/foo/var/bar"));
    dir->addEvent(IOEvent::createEventFromErrno(3, EACCES, "/home/foo/var/car"));
    dir->addEvent(IOEvent::createEventFromErrno(4, EISDIR, "/home/foo/var/var"));
    std::string expected("\n"
                         "Disk 1: IOEvent(IO_FAILURE, Input/output error: "
                         "/home/foo/var/foo, time 1)\n"
                         "Dir /home/foo/var: IOEvent(INTERNAL_FAILURE, Bad file "
                         "descriptor: /home/foo/var/bar, time 2)\n"
                         "Dir /home/foo/var: IOEvent(NO_PERMISSION, Permission denied: "
                         "/home/foo/var/car, time 3)\n"
                         "Dir /home/foo/var: IOEvent(PATH_FAILURE, Is a directory: "
                         "/home/foo/var/var, time 4)\n"
    );
    CPPUNIT_ASSERT_EQUAL(expected, l.ost.str());
}

void DeviceManagerTest::testXml()
{
    DeviceManager manager(DeviceMapper::UP(new SimpleDeviceMapper), _clock);
    Directory::SP dir(manager.getDirectory("/home/", 0));
    dir->getPartition().initializeMonitor();
    std::string xml = manager.toXml("  ");
    CPPUNIT_ASSERT_MSG(xml,
                       xml.find("<partitionmonitor>") != std::string::npos);
}

}

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/memfilepersistence/device/devicemanager.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>

namespace storage {

namespace memfile {

class DevicesTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(DevicesTest);
    CPPUNIT_TEST(testDisk);
    CPPUNIT_TEST(testPartition);
    CPPUNIT_TEST(testDirectory);
    CPPUNIT_TEST_SUITE_END();

public:
    void testDisk();
    void testPartition();
    void testDirectory();

    framework::defaultimplementation::FakeClock _clock;
};

CPPUNIT_TEST_SUITE_REGISTRATION(DevicesTest);

void DevicesTest::testDisk()
{
    DeviceManager manager(DeviceMapper::UP(new SimpleDeviceMapper), _clock);
    Disk::SP disk1(manager.getDisk("/something/on/disk"));
    Disk::SP disk2(manager.getDisk("/something/on/disk"));
    CPPUNIT_ASSERT_EQUAL(disk1->getId(), disk2->getId());
    CPPUNIT_ASSERT_EQUAL(disk1.get(), disk2.get());
    Disk::SP disk3(manager.getDisk("/something/on/disk2"));
    CPPUNIT_ASSERT(disk2->getId() != disk3->getId());
    disk3->toString(); // Add code coverage
}

void DevicesTest::testPartition()
{
    DeviceManager manager(DeviceMapper::UP(new SimpleDeviceMapper), _clock);
    Partition::SP part(manager.getPartition("/etc"));
    CPPUNIT_ASSERT_EQUAL(std::string("/etc"), part->getMountPoint());
    part->toString(); // Add code coverage
}

void DevicesTest::testDirectory()
{
    DeviceManager manager(DeviceMapper::UP(new SimpleDeviceMapper), _clock);
    Directory::SP dir1(manager.getDirectory("/on/disk", 0));
    CPPUNIT_ASSERT_EQUAL(std::string("/on/disk"), dir1->getPath());
    CPPUNIT_ASSERT(dir1->getLastEvent() == 0);
    CPPUNIT_ASSERT_EQUAL(Device::OK, dir1->getState());
    CPPUNIT_ASSERT(dir1->isOk());
    CPPUNIT_ASSERT_EQUAL(std::string("/on/disk 0"), dir1->toString());

    dir1->addEvent(Device::IO_FAILURE, "Ouch", "");
    CPPUNIT_ASSERT(!dir1->isOk());
    CPPUNIT_ASSERT(dir1->getLastEvent() != 0);
    CPPUNIT_ASSERT_EQUAL(std::string("/on/disk 5 0 Ouch"), dir1->toString());
    dir1->toString(); // Add code coverage
}

}

} // storage

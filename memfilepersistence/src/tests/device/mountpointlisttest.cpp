// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/memfilepersistence/device/mountpointlist.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/vdstestlib/cppunit/macros.h>

using vespalib::fileExists;
using vespalib::isDirectory;
using vespalib::isSymLink;
using vespalib::readLink;

namespace storage {

namespace memfile {

class MountPointList_Test : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(MountPointList_Test);
    CPPUNIT_TEST(testScanning);
    CPPUNIT_TEST(testStatusFile);
    CPPUNIT_TEST(testInitDisks);
    CPPUNIT_TEST_SUITE_END();

    static const std::string _prefix;

public:
    void testScanning();
    void testStatusFile();
    void testInitDisks();

    void init();
    void tearDown() override;

    framework::defaultimplementation::FakeClock _clock;

private:
    DeviceManager::UP newDeviceManager() {
        return DeviceManager::UP(
                new DeviceManager(
                        DeviceMapper::UP(new SimpleDeviceMapper),
                        _clock));
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(MountPointList_Test);

const std::string MountPointList_Test::_prefix("./vdsroot");

namespace {
    void run(const std::string& cmd) {
        CPPUNIT_ASSERT_MESSAGE(cmd, system(cmd.c_str()) == 0);
    }
}

void MountPointList_Test::init()
{
    tearDown();
    run("rm -rf "+_prefix);
    run("mkdir -p "+_prefix+"/disks");

    run("mkdir "+_prefix+"/disks/d0");    // Regular dir
    run("mkdir "+_prefix+"/disks/d1");    // Inaccessible dir
    run("chmod 000 "+_prefix+"/disks/d1");
    run("mkdir "+_prefix+"/disks/D2");    // Wrongly named dir
    run("mkdir "+_prefix+"/disks/d3");    // Regular non-empty dir
    run("touch "+_prefix+"/disks/d3/foo");
    run("touch "+_prefix+"/disks/d4");    // Not a dir
    run("ln -s D2 "+_prefix+"/disks/d5"); // Symlink to dir
    run("ln -s d4 "+_prefix+"/disks/d6"); // Symlink to file
}

void MountPointList_Test::tearDown()
{
    try{
        if (fileExists(_prefix+"/disks/d1")) {
            run("chmod 755 "+_prefix+"/disks/d1");
        }
    } catch (std::exception& e) {
        std::cerr << "Failed to clean up: " << e.what() << "\n";
    }
}

void MountPointList_Test::testScanning()
{
    init();
    MountPointList list(_prefix,
                        std::vector<vespalib::string>(),
                        DeviceManager::UP(
                                new DeviceManager(
                                        DeviceMapper::UP(new SimpleDeviceMapper),
                                        _clock)));
    list.scanForDisks();

        // Check that we got the expected entries.
    CPPUNIT_ASSERT_EQUAL(7u, list.getSize());

    for (uint32_t i=0; i<7u; ++i) {
        std::ostringstream ost;
        ost << _prefix << "/disks/d" << i;
        CPPUNIT_ASSERT_EQUAL(ost.str(), list[i].getPath());
    }

    // Note.. scanForDisks() should not in any circumstances access the
    // disks. Thus it should not know that d1 is inaccessible, or that d6
    // is actually a symlink to a file
    CPPUNIT_ASSERT_EQUAL(Device::OK,           list[0].getState());
    CPPUNIT_ASSERT_EQUAL(Device::OK,           list[1].getState());
    CPPUNIT_ASSERT_EQUAL(Device::NOT_FOUND,    list[2].getState());
    CPPUNIT_ASSERT_EQUAL(Device::OK,           list[3].getState());
    CPPUNIT_ASSERT_EQUAL(Device::PATH_FAILURE, list[4].getState());
    CPPUNIT_ASSERT_EQUAL(Device::OK,           list[5].getState());
    CPPUNIT_ASSERT_EQUAL(Device::OK,           list[6].getState());

    list.verifyHealthyDisks(-1);
    CPPUNIT_ASSERT_EQUAL(Device::OK,               list[0].getState());
    CPPUNIT_ASSERT_EQUAL(Device::NO_PERMISSION,    list[1].getState());
    CPPUNIT_ASSERT_EQUAL(Device::NOT_FOUND,        list[2].getState());
    CPPUNIT_ASSERT_EQUAL(Device::INTERNAL_FAILURE, list[3].getState());
    CPPUNIT_ASSERT_EQUAL(Device::PATH_FAILURE,     list[4].getState());
    CPPUNIT_ASSERT_EQUAL(Device::OK,               list[5].getState());
    CPPUNIT_ASSERT_EQUAL(Device::PATH_FAILURE,     list[6].getState());
}

void MountPointList_Test::testStatusFile()
{
    init();
    std::string statusFileName(_prefix + "/disks.status");

    // Try reading non-existing file, and writing a file
    {
        MountPointList list(_prefix,
                            std::vector<vespalib::string>(),
                            DeviceManager::UP(
                                    new DeviceManager(
                                            DeviceMapper::UP(new SimpleDeviceMapper),
                                            _clock)));

        _clock.setAbsoluteTimeInSeconds(5678);
        list.scanForDisks();

        // File does not currently exist, that should be ok though.
        list.readFromFile();
        list.verifyHealthyDisks(-1);
        CPPUNIT_ASSERT_EQUAL(7u, list.getSize());
        list[5].addEvent(IOEvent(1234, Device::IO_FAILURE, "Argh", "Hmm"));
        CPPUNIT_ASSERT_EQUAL(Device::IO_FAILURE, list[5].getState());

        // Write to file.
        list.writeToFile();
    }

    // Check contents of file.
    {
        std::ifstream in(statusFileName.c_str());
        std::string line;
        CPPUNIT_ASSERT(std::getline(in, line));

        CPPUNIT_ASSERT_PREFIX(
                std::string(_prefix + "/disks/d1 3 5678 IoException: NO PERMISSION: "
                            "open(./vdsroot/disks/d1/chunkinfo, 0x1): Failed, "
                            "errno(13): Permission denied"),
                line);
        CPPUNIT_ASSERT(std::getline(in, line));
        CPPUNIT_ASSERT_PREFIX(
                std::string(_prefix +"/disks/d2 1 5678 Disk not found during scanning of "
                            "disks directory"),
                line);
        CPPUNIT_ASSERT(std::getline(in, line));
        CPPUNIT_ASSERT_PREFIX(
                std::string(_prefix + "/disks/d3 4 5678 Foreign data in mountpoint. New "
                            "mountpoints added should be empty."),
                line);
        CPPUNIT_ASSERT(std::getline(in, line));
        CPPUNIT_ASSERT_PREFIX(
                std::string(_prefix + "/disks/d4 2 5678 File d4 in disks directory is not "
                            "a directory."),
                line);
        CPPUNIT_ASSERT(std::getline(in, line));
        CPPUNIT_ASSERT_PREFIX(std::string(_prefix + "/disks/d5 5 1234 Argh"),
                              line);
        CPPUNIT_ASSERT(std::getline(in, line));
        CPPUNIT_ASSERT_PREFIX(
                std::string(_prefix + "/disks/d6 2 5678 The path exist, but is not a "
                            "directory."),
                line);
        CPPUNIT_ASSERT(std::getline(in, line));
        CPPUNIT_ASSERT_EQUAL(std::string("EOF"), line);
    }

    // Starting over to get new device instances.
    // Scan disk, read file, and check that erronious disks are not used.
    {
        MountPointList list(_prefix,
                            std::vector<vespalib::string>(),
                            DeviceManager::UP(
                                    new DeviceManager(
                                            DeviceMapper::UP(new SimpleDeviceMapper),
                                            _clock)));
        list.scanForDisks();
        list.readFromFile();
            // Check that we got the expected entries.
        CPPUNIT_ASSERT_EQUAL(7u, list.getSize());

        // Note.. scanForDisks() should not under any circumstance access the
        // disks. Thus it should not know that d1 is inaccessible.
        CPPUNIT_ASSERT_EQUAL(Device::OK,            list[0].getState());
        CPPUNIT_ASSERT_EQUAL(Device::NO_PERMISSION, list[1].getState());
        CPPUNIT_ASSERT_EQUAL(Device::NOT_FOUND,     list[2].getState());
        CPPUNIT_ASSERT_EQUAL(Device::INTERNAL_FAILURE, list[3].getState());
        CPPUNIT_ASSERT_EQUAL(Device::PATH_FAILURE,  list[4].getState());
        CPPUNIT_ASSERT_EQUAL(Device::IO_FAILURE,    list[5].getState());
        CPPUNIT_ASSERT_EQUAL(Device::PATH_FAILURE,  list[6].getState());
    }
}

void MountPointList_Test::testInitDisks()
{
    vespalib::string d3target = "d3target";
    vespalib::string foodev = _prefix + "/foodev";
    vespalib::string bardev = _prefix + "/bardev";

    tearDown();
    run("rm -rf " + _prefix);
    run("mkdir -p " + _prefix + "/disks/d2");
    run("ln -s " + d3target + " " + _prefix + "/disks/d3");

    std::vector<vespalib::string> diskPaths {
        // disks/d0 should become a regular directory
        _prefix + "/disks/d0",
        // disks/d1 should be a symlink to /foo
        foodev,
        // disks/d2 should already be a directory
        "/ignored",
        // disks/d3 should already be a symlink
        "/ignored2"
    };

    MountPointList list(_prefix, diskPaths, newDeviceManager());
    list.initDisks();

    CPPUNIT_ASSERT(isDirectory(_prefix + "/disks"));
    CPPUNIT_ASSERT(isDirectory(_prefix + "/disks/d0"));
    CPPUNIT_ASSERT(isSymLink(_prefix + "/disks/d1"));
    CPPUNIT_ASSERT_EQUAL(foodev, readLink(_prefix + "/disks/d1"));
    CPPUNIT_ASSERT(isDirectory(_prefix + "/disks/d2"));
    CPPUNIT_ASSERT(isSymLink(_prefix + "/disks/d3"));
    CPPUNIT_ASSERT_EQUAL(d3target, readLink(_prefix + "/disks/d3"));
}

} // memfile

} // storage

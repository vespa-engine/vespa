// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/memfilepersistence/tools/vdsdisktool.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/util/programoptions_testutils.h>
#include <tests/spi/memfiletestutils.h>

namespace storage {
namespace memfile {

struct VdsDiskToolTest : public SingleDiskMemFileTestUtils
{
    framework::defaultimplementation::FakeClock _clock;

    void setUp();
    void setupRoot();

    void testSimple();

    CPPUNIT_TEST_SUITE(VdsDiskToolTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(VdsDiskToolTest);

#define ASSERT_MATCH(optstring, pattern, exitcode) \
{ \
    std::ostringstream out; \
    int result = 1; \
    try{ \
        vespalib::AppOptions opts("vdsdisktool " optstring); \
        result = VdsDiskTool::run(opts.getArgCount(), opts.getArguments(), \
                                  "vdsroot", out, out); \
    } catch (std::exception& e) { \
        out << "Application aborted with exception:\n" << e.what() << "\n"; \
    } \
    CPPUNIT_ASSERT_MATCH_REGEX(pattern, out.str()); \
    CPPUNIT_ASSERT_EQUAL(exitcode, result); \
}

namespace {
    void createDisk(int i) {
        std::ostringstream path;
        path << "vdsroot/mycluster/storage/3/disks/d" << i;
        CPPUNIT_ASSERT_EQUAL(0, system(("mkdir -p " + path.str()).c_str()));
    }
}

void
VdsDiskToolTest::setUp()
{
    system("rm -rf vdsroot");
}

void
VdsDiskToolTest::setupRoot()
{
    system("rm -rf vdsroot");
    createDisk(0);
}

void
VdsDiskToolTest::testSimple()
{
        // Test syntax page
    ASSERT_MATCH("--help", ".*Usage: vdsdisktool .*", 0);
        // No VDS installation
    ASSERT_MATCH("status", ".*No VDS installations found at all.*", 1);
        // Common setup
    setupRoot();
    ASSERT_MATCH("status", ".*Disks on storage node 3 in cluster mycluster:\\s*"
                           "Disk 0: OK\\s*", 0);
        // Two disks
    system("mkdir -p vdsroot/mycluster/storage/3/disks/d1/");
    ASSERT_MATCH("status", ".*Disks on storage node 3 in cluster mycluster:\\s*"
                           "Disk 0: OK\\s*"
                           "Disk 1: OK\\s*", 0);
        // Two disks, non-continuous indexes
    system("rm -rf vdsroot/mycluster/storage/3/disks/d1/");
    system("mkdir -p vdsroot/mycluster/storage/3/disks/d2/");
    ASSERT_MATCH("status", ".*Disks on storage node 3 in cluster mycluster:\\s*"
                           "Disk 0: OK\\s*"
                           "Disk 1: NOT_FOUND - Disk not found during scan.*"
                           "Disk 2: OK\\s*", 0);
        // Status file existing
    setupRoot();
    createDisk(1);
    MountPointList mountPoints("vdsroot/mycluster/storage/3",
                               std::vector<vespalib::string>(),
                               std::make_unique<DeviceManager>(std::make_unique<SimpleDeviceMapper>(), _clock));
    mountPoints.scanForDisks();
    CPPUNIT_ASSERT_EQUAL(2u, mountPoints.getSize());
    mountPoints[1].addEvent(Device::IO_FAILURE, "Bad", "Found in test");
    mountPoints.writeToFile();
    ASSERT_MATCH("status", ".*Disks on storage node 3 in cluster mycluster:\\s*"
                           "Disk 0: OK\\s*"
                           "Disk 1: IO_FAILURE - 0 Bad\\s*", 0);
}

} // memfile
} // storage

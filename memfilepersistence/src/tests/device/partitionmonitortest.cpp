// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/memfilepersistence/device/partitionmonitor.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {

namespace memfile {

struct PartitionMonitorTest : public CppUnit::TestFixture
{
    void testNormalUsage();
    void testHighInodeFillrate();
    void testAlwaysStatPolicy();
    void testPeriodPolicy();
    void testStatOncePolicy();
    void testDynamicPolicy();
    void testIsFull();

    CPPUNIT_TEST_SUITE(PartitionMonitorTest);
    CPPUNIT_TEST(testNormalUsage);
    CPPUNIT_TEST(testHighInodeFillrate);
    CPPUNIT_TEST(testAlwaysStatPolicy);
    CPPUNIT_TEST(testPeriodPolicy);
    CPPUNIT_TEST(testStatOncePolicy);
    CPPUNIT_TEST(testDynamicPolicy);
    CPPUNIT_TEST(testIsFull);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(PartitionMonitorTest);

struct FakeStatter : public PartitionMonitor::Statter {
    struct statvfs _info;

    FakeStatter() {
        _info.f_bsize = 4096;
        _info.f_frsize = 4096;
        _info.f_blocks = 1000;
        _info.f_bfree = 500;
        _info.f_bavail = 400;
        _info.f_files = 64;
        _info.f_ffree = 32;
        _info.f_favail = 30;
        _info.f_fsid = 13;
        _info.f_namemax = 256;
    }
    void removeData(uint32_t size) {
        _info.f_bavail += (size / _info.f_bsize);
        _info.f_bfree += (size / _info.f_bsize);
    }
    void addData(uint32_t size) {
        _info.f_bavail -= (size / _info.f_bsize);
        _info.f_bfree -= (size / _info.f_bsize);
    }

    void statFileSystem(const std::string&, struct statvfs& info) override {
        info = _info;
    }
};

void PartitionMonitorTest::testNormalUsage()
{
    const std::string file_name = TEST_PATH("testrunner.cpp");
    PartitionMonitor monitor(file_name);
    FakeStatter* statter = new FakeStatter();
    monitor.setStatter(std::unique_ptr<PartitionMonitor::Statter>(statter));
    std::string expected(
            "PartitionMonitor(" + file_name + ", STAT_PERIOD(100), "
            "2048000/3686400 used - 55.5556 % full)");
    CPPUNIT_ASSERT_EQUAL(expected, monitor.toString(false));
    expected =
            "PartitionMonitor(" + file_name + ") {\n"
            "  Fill rate: 55.5556 %\n"
            "  Inode fill rate: 51.6129 %\n"
            "  Detected block size: 4096\n"
            "  File system id: 13\n"
            "  Total size: 3686400 (3600 kB)\n"
            "  Used size: 2048000 (2000 kB)\n"
            "  Queries since last stat: 0\n"
            "  Monitor policy: STAT_PERIOD(100)\n"
            "  Root only ratio 0\n"
            "  Max fill rate 98 %\n"
            "}";
    CPPUNIT_ASSERT_EQUAL(expected, monitor.toString(true));
    CPPUNIT_ASSERT(monitor.getFillRate() > 0.55);
}

void PartitionMonitorTest::testHighInodeFillrate()
{
    const std::string file_name = TEST_PATH("testrunner.cpp");
    PartitionMonitor monitor(file_name);
    FakeStatter* statter = new FakeStatter();
    statter->_info.f_favail = 2;
    monitor.setStatter(std::unique_ptr<PartitionMonitor::Statter>(statter));
    std::string expected(
            "PartitionMonitor(" + file_name + ", STAT_PERIOD(100), "
            "2048000/3686400 used - 94.1176 % full (inodes))");
    CPPUNIT_ASSERT_EQUAL(expected, monitor.toString(false));
    expected =
            "PartitionMonitor(" + file_name + ") {\n"
            "  Fill rate: 55.5556 %\n"
            "  Inode fill rate: 94.1176 %\n"
            "  Detected block size: 4096\n"
            "  File system id: 13\n"
            "  Total size: 3686400 (3600 kB)\n"
            "  Used size: 2048000 (2000 kB)\n"
            "  Queries since last stat: 0\n"
            "  Monitor policy: STAT_PERIOD(100)\n"
            "  Root only ratio 0\n"
            "  Max fill rate 98 %\n"
            "}";
    CPPUNIT_ASSERT_EQUAL(expected, monitor.toString(true));
    CPPUNIT_ASSERT(monitor.getFillRate() > 0.94);
}

void PartitionMonitorTest::testAlwaysStatPolicy()
{
    PartitionMonitor monitor(TEST_PATH("testrunner.cpp"));
    FakeStatter* statter = new FakeStatter();
    monitor.setStatter(std::unique_ptr<PartitionMonitor::Statter>(statter));
    monitor.setAlwaysStatPolicy();
    for (uint32_t i=0; i<10; ++i) {
        monitor.getFillRate();
        CPPUNIT_ASSERT_EQUAL(0u, monitor._queriesSinceStat);
    }
}

void PartitionMonitorTest::testPeriodPolicy()
{
    PartitionMonitor monitor(TEST_PATH("testrunner.cpp"));
    FakeStatter* statter = new FakeStatter();
    monitor.setStatter(std::unique_ptr<PartitionMonitor::Statter>(statter));
    monitor.setStatPeriodPolicy(4);
    for (uint32_t i=1; i<16; ++i) {
        monitor.getFillRate();
        CPPUNIT_ASSERT_EQUAL(i % 4, monitor._queriesSinceStat);
    }
}

void PartitionMonitorTest::testStatOncePolicy()
{
    PartitionMonitor monitor(TEST_PATH("testrunner.cpp"));
    FakeStatter* statter = new FakeStatter();
    monitor.setStatter(std::unique_ptr<PartitionMonitor::Statter>(statter));
    monitor.setStatOncePolicy();
    for (uint32_t i=1; i<16; ++i) {
        monitor.getFillRate();
        CPPUNIT_ASSERT_EQUAL(i, monitor._queriesSinceStat);
    }
}

void PartitionMonitorTest::testDynamicPolicy()
{
    PartitionMonitor monitor(TEST_PATH("testrunner.cpp"));
    FakeStatter* statter = new FakeStatter();
    monitor.setStatter(std::unique_ptr<PartitionMonitor::Statter>(statter));
    monitor.setStatDynamicPolicy(2);
        // Add some data, such that we see that period goes down
    CPPUNIT_ASSERT_EQUAL(uint64_t(3698), monitor.calcDynamicPeriod());
    CPPUNIT_ASSERT_EQUAL(55, (int) (100 * monitor.getFillRate()));
    monitor.addingData(256 * 1024);
    CPPUNIT_ASSERT_EQUAL(uint64_t(2592), monitor.calcDynamicPeriod());
    CPPUNIT_ASSERT_EQUAL(62, (int) (100 * monitor.getFillRate()));
    monitor.addingData(512 * 1024);
    CPPUNIT_ASSERT_EQUAL(uint64_t(968), monitor.calcDynamicPeriod());
    CPPUNIT_ASSERT_EQUAL(76, (int) (100 * monitor.getFillRate()));
        // Add such that we hint that we have more data than possible on disk
    monitor.addingData(1024 * 1024);
        // Let fake stat just have a bit more data than before
    statter->addData(256 * 1024);
        // With high fill rate, we should check stat each time
    CPPUNIT_ASSERT_EQUAL(uint64_t(1), monitor.calcDynamicPeriod());
        // As period is 1, we will now do a new stat, it should find we
        // actually have less fill rate
    CPPUNIT_ASSERT_EQUAL(62, (int) (100 * monitor.getFillRate()));
}

void PartitionMonitorTest::testIsFull()
{
    PartitionMonitor monitor(TEST_PATH("testrunner.cpp"));
    monitor.setMaxFillness(0.85);
    FakeStatter* statter = new FakeStatter();
    monitor.setStatOncePolicy();
    monitor.setStatter(std::unique_ptr<PartitionMonitor::Statter>(statter));

    CPPUNIT_ASSERT_EQUAL(55, (int) (100 * monitor.getFillRate()));
    CPPUNIT_ASSERT(!monitor.isFull());
    monitor.addingData(512 * 1024);
    CPPUNIT_ASSERT_EQUAL(69, (int) (100 * monitor.getFillRate()));
    CPPUNIT_ASSERT(!monitor.isFull());
    monitor.addingData(600 * 1024);
    CPPUNIT_ASSERT_EQUAL(86, (int) (100 * monitor.getFillRate()));
    CPPUNIT_ASSERT(monitor.isFull());
    monitor.removingData(32 * 1024);
    CPPUNIT_ASSERT_EQUAL(85, (int) (100 * monitor.getFillRate()));
    CPPUNIT_ASSERT(monitor.isFull());
    monitor.removingData(32 * 1024);
    CPPUNIT_ASSERT_EQUAL(84, (int) (100 * monitor.getFillRate()));
    CPPUNIT_ASSERT(!monitor.isFull());
}

}

} // storage

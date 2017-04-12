// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <boost/lexical_cast.hpp>
#include <cppunit/extensions/HelperMacros.h>
#include <vespa/log/log.h>
#include <vespa/storage/bucketdb/bucketmanager.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/storageserver/bucketintegritychecker.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <tests/common/testhelper.h>
#include <tests/common/storagelinktest.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/vespalib/io/fileutil.h>
#include <tests/common/teststorageapp.h>

LOG_SETUP(".test.bucketintegritychecker");

namespace storage {

struct BucketIntegrityCheckerTest : public CppUnit::TestFixture {
    std::unique_ptr<vdstestlib::DirConfig> _config;
    std::unique_ptr<TestServiceLayerApp> _node;
    int _timeout; // Timeout in seconds before aborting

    void setUp() override {
        _timeout = 60*2;
        _config.reset(new vdstestlib::DirConfig(getStandardConfig(true)));
        _node.reset(new TestServiceLayerApp(DiskCount(256),
                                            NodeIndex(0),
                                            _config->getConfigId()));
    }

    void tearDown() override {
        LOG(info, "Finished test");
    }

    void testConfig();
    void testBasicFunctionality();
    void testTiming();

    CPPUNIT_TEST_SUITE(BucketIntegrityCheckerTest);
    CPPUNIT_TEST(testConfig);
    CPPUNIT_TEST(testBasicFunctionality);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(BucketIntegrityCheckerTest);

void BucketIntegrityCheckerTest::testConfig()
{

    // Verify that config is read correctly. Given config should not use
    // any default values.
    vdstestlib::DirConfig::Config& config(
            _config->getConfig("stor-integritychecker"));
    config.set("dailycyclestart", "60");
    config.set("dailycyclestop", "360");
    config.set("weeklycycle", "crRc-rc");
    config.set("maxpending", "2");
    config.set("mincycletime", "120");
    config.set("requestdelay", "5");

    BucketIntegrityChecker checker(_config->getConfigId(),
                                   _node->getComponentRegister());
    checker.setMaxThreadWaitTime(framework::MilliSecTime(10));
    SchedulingOptions& opt(checker.getSchedulingOptions());
    CPPUNIT_ASSERT_EQUAL(60u, opt._dailyCycleStart);
    CPPUNIT_ASSERT_EQUAL(360u, opt._dailyCycleStop);
    CPPUNIT_ASSERT_EQUAL(SchedulingOptions::CONTINUE,  opt._dailyStates[0]);
    CPPUNIT_ASSERT_EQUAL(SchedulingOptions::RUN_CHEAP, opt._dailyStates[1]);
    CPPUNIT_ASSERT_EQUAL(SchedulingOptions::RUN_FULL,  opt._dailyStates[2]);
    CPPUNIT_ASSERT_EQUAL(SchedulingOptions::CONTINUE,  opt._dailyStates[3]);
    CPPUNIT_ASSERT_EQUAL(SchedulingOptions::DONT_RUN,  opt._dailyStates[4]);
    CPPUNIT_ASSERT_EQUAL(SchedulingOptions::RUN_CHEAP, opt._dailyStates[5]);
    CPPUNIT_ASSERT_EQUAL(SchedulingOptions::CONTINUE,  opt._dailyStates[6]);
    CPPUNIT_ASSERT_EQUAL(2u, opt._maxPendingCount);
    CPPUNIT_ASSERT_EQUAL(framework::SecondTime(7200), opt._minCycleTime);
    CPPUNIT_ASSERT_EQUAL(framework::SecondTime(5), opt._requestDelay);
}

namespace {
    /**
     * Calculate a date based on the following format:
     *   week<#> <day> <hh>:<mm>:<ss>
     * Examples: "week3 mon 00:30:00"
     *           "week3 tue 04:20:00"
     *           "week9 thi 14:00:24"
     */
    time_t getDate(const std::string& datestring) {
        vespalib::string rest(datestring);
        int spacePos = rest.find(' ');
        uint32_t week = strtoul(rest.substr(4, spacePos-4).c_str(), NULL, 0);
        rest = rest.substr(spacePos+1);
        vespalib::string wday(rest.substr(0,3));
        rest = rest.substr(4);
        uint32_t hours = strtoul(rest.substr(0, 2).c_str(), NULL, 0);
        uint32_t minutes = strtoul(rest.substr(3, 2).c_str(), NULL, 0);
        uint32_t seconds = strtoul(rest.substr(6, 2).c_str(), NULL, 0);
        uint32_t day(0);
        if      (wday == "mon") { day = 1; }
        else if (wday == "tue") { day = 2; }
        else if (wday == "wed") { day = 3; }
        else if (wday == "thi") { day = 4; }
        else if (wday == "fri") { day = 5; }
        else if (wday == "sat") { day = 6; }
        else if (wday == "sun") { day = 0; }
        else { assert(false); }
        // Create a start time that points to the start of some week.
        // A random sunday 00:00:00, which we will use as start of time
        struct tm mytime;
        memset(&mytime, 0, sizeof(mytime));
        mytime.tm_year = 2008 - 1900;
        mytime.tm_mon = 0;
        mytime.tm_mday = 1;
        mytime.tm_hour = 0;
        mytime.tm_min = 0;
        mytime.tm_sec = 0;
        time_t startTime = timegm(&mytime);
        CPPUNIT_ASSERT(gmtime_r(&startTime, &mytime));
        while (mytime.tm_wday != 0) {
            ++mytime.tm_mday;
            startTime = timegm(&mytime);
            CPPUNIT_ASSERT(gmtime_r(&startTime, &mytime));
        }
            // Add the wanted values to the start time
        time_t resultTime = startTime;
        resultTime += week * 7 * 24 * 60 * 60
                    + day      * 24 * 60 * 60
                    + hours         * 60 * 60
                    + minutes            * 60
                    + seconds;
        // std::cerr << "Time requested " << datestring << ". Got time "
        // << framework::SecondTime(resultTime).toString() << "\n";
        return resultTime;
    }

    void addBucketToDatabase(TestServiceLayerApp& server,
                             const document::BucketId& id, uint8_t disk,
                             uint32_t numDocs, uint32_t crc, uint32_t totalSize)
    {
        bucketdb::StorageBucketInfo info;
        info.setBucketInfo(api::BucketInfo(crc, numDocs, totalSize));
        info.disk = disk;
        server.getStorageBucketDatabase().insert(id, info, "foo");
    }


    /**
     * In tests wanting to only have one pending, only add buckets for one disk
     * as pending is per disk. If so set singleDisk true.
     */
    void addBucketsToDatabase(TestServiceLayerApp& server, bool singleDisk) {
        addBucketToDatabase(server, document::BucketId(16, 0x123), 0,
                            14, 0x123, 1024);
        addBucketToDatabase(server, document::BucketId(16, 0x234), 0,
                            18, 0x234, 1024);
        addBucketToDatabase(server, document::BucketId(16, 0x345), 0,
                            11, 0x345, 2048);
        addBucketToDatabase(server, document::BucketId(16, 0x456), 0,
                            13, 0x456, 1280);
        if (!singleDisk) {
            addBucketToDatabase(server, document::BucketId(16, 0x567), 1,
                                20, 0x567, 4096);
            addBucketToDatabase(server, document::BucketId(16, 0x987), 254,
                                8, 0x987, 65536);
        }
    }
}

void BucketIntegrityCheckerTest::testBasicFunctionality()
{
    _node->getClock().setAbsoluteTimeInSeconds(getDate("week1 sun 00:00:00"));
    addBucketsToDatabase(*_node, false);
    DummyStorageLink* dummyLink = 0;
    {
        std::unique_ptr<BucketIntegrityChecker> midLink(
            new BucketIntegrityChecker("", _node->getComponentRegister()));
        BucketIntegrityChecker& checker(*midLink);
        checker.setMaxThreadWaitTime(framework::MilliSecTime(10));
        // Setup and start checker
        DummyStorageLink topLink;
        topLink.push_back(StorageLink::UP(midLink.release()));
        checker.push_back(std::unique_ptr<StorageLink>(
                    dummyLink = new DummyStorageLink()));
        checker.getSchedulingOptions()._maxPendingCount = 2;
        checker.getSchedulingOptions()._minCycleTime = framework::SecondTime(60 * 60);
        topLink.open();
        // Waiting for system to be initialized
        FastOS_Thread::Sleep(10); // Give next message chance to come
        ASSERT_COMMAND_COUNT(0, *dummyLink);
        topLink.doneInit();
        checker.bump();
        // Should have started new run with 2 pending per disk
        dummyLink->waitForMessages(4, _timeout);
        FastOS_Thread::Sleep(10); // Give 5th message chance to come
        ASSERT_COMMAND_COUNT(4, *dummyLink);
        RepairBucketCommand *cmd1 = dynamic_cast<RepairBucketCommand*>(
                dummyLink->getCommand(0).get());
        CPPUNIT_ASSERT_EQUAL(230, (int)cmd1->getPriority());
        CPPUNIT_ASSERT(cmd1);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 0x234),
                             cmd1->getBucketId());
        RepairBucketCommand *cmd2 = dynamic_cast<RepairBucketCommand*>(
                dummyLink->getCommand(1).get());
        CPPUNIT_ASSERT(cmd2);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 0x456),
                             cmd2->getBucketId());
        RepairBucketCommand *cmd3 = dynamic_cast<RepairBucketCommand*>(
                dummyLink->getCommand(2).get());
        CPPUNIT_ASSERT(cmd3);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 0x567),
                             cmd3->getBucketId());
        RepairBucketCommand *cmd4 = dynamic_cast<RepairBucketCommand*>(
                dummyLink->getCommand(3).get());
        CPPUNIT_ASSERT(cmd4);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 0x987),
                             cmd4->getBucketId());

        // Answering a message on disk with no more buckets does not trigger new
        std::shared_ptr<RepairBucketReply> reply1(
                new RepairBucketReply(*cmd3));
        CPPUNIT_ASSERT(StorageLinkTest::callOnUp(checker, reply1));
        FastOS_Thread::Sleep(10); // Give next message chance to come
        ASSERT_COMMAND_COUNT(4, *dummyLink);
        // Answering a message on disk with more buckets trigger new repair
        std::shared_ptr<RepairBucketReply> reply2(
                new RepairBucketReply(*cmd2));
        CPPUNIT_ASSERT(StorageLinkTest::callOnUp(checker, reply2));
        dummyLink->waitForMessages(5, _timeout);
        FastOS_Thread::Sleep(10); // Give 6th message chance to come
        ASSERT_COMMAND_COUNT(5, *dummyLink);
        RepairBucketCommand *cmd5 = dynamic_cast<RepairBucketCommand*>(
                dummyLink->getCommand(4).get());
        CPPUNIT_ASSERT(cmd5);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 0x345),
                             cmd5->getBucketId());
        // Fail a repair, causing it to be resent later, but first continue
        // with other bucket.
        std::shared_ptr<RepairBucketReply> reply3(
                new RepairBucketReply(*cmd1));
        reply3->setResult(api::ReturnCode(api::ReturnCode::IGNORED));
        CPPUNIT_ASSERT(StorageLinkTest::callOnUp(checker, reply3));
        dummyLink->waitForMessages(6, _timeout);
        FastOS_Thread::Sleep(10); // Give 7th message chance to come
        ASSERT_COMMAND_COUNT(6, *dummyLink);
        RepairBucketCommand *cmd6 = dynamic_cast<RepairBucketCommand*>(
                dummyLink->getCommand(5).get());
        CPPUNIT_ASSERT(cmd6);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 0x123),
                             cmd6->getBucketId());
        // Fail a repair with not found. That is an acceptable return code.
        // (No more requests as this was last for that disk)
        std::shared_ptr<RepairBucketReply> reply4(
                new RepairBucketReply(*cmd4));
        reply3->setResult(api::ReturnCode(api::ReturnCode::BUCKET_NOT_FOUND));
        CPPUNIT_ASSERT(StorageLinkTest::callOnUp(checker, reply4));
        FastOS_Thread::Sleep(10); // Give 7th message chance to come
        ASSERT_COMMAND_COUNT(6, *dummyLink);

        // Send a repair reply that actually have corrected the bucket.
        api::BucketInfo newInfo(0x3456, 4, 8192);
        std::shared_ptr<RepairBucketReply> reply5(
                new RepairBucketReply(*cmd5, newInfo));
        reply5->setAltered(true);
        CPPUNIT_ASSERT(StorageLinkTest::callOnUp(checker, reply5));

        // Finish run. New iteration should not start yet as min
        // cycle time has not passed
        std::shared_ptr<RepairBucketReply> reply6(
                new RepairBucketReply(*cmd6));
        CPPUNIT_ASSERT(StorageLinkTest::callOnUp(checker, reply6));
        dummyLink->waitForMessages(7, _timeout);
        ASSERT_COMMAND_COUNT(7, *dummyLink);
        RepairBucketCommand *cmd7 = dynamic_cast<RepairBucketCommand*>(
                dummyLink->getCommand(6).get());
        CPPUNIT_ASSERT(cmd7);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 0x234),
                             cmd7->getBucketId());
        std::shared_ptr<RepairBucketReply> reply7(
                new RepairBucketReply(*cmd7));
        CPPUNIT_ASSERT(StorageLinkTest::callOnUp(checker, reply7));
        FastOS_Thread::Sleep(10); // Give 8th message chance to come
        ASSERT_COMMAND_COUNT(7, *dummyLink);

        // Still not time for next iteration
        dummyLink->reset();
        _node->getClock().setAbsoluteTimeInSeconds(getDate("week1 sun 00:59:59"));
        FastOS_Thread::Sleep(10); // Give new run chance to start
        ASSERT_COMMAND_COUNT(0, *dummyLink);

        // Pass time until next cycle should start
        dummyLink->reset();
        _node->getClock().setAbsoluteTimeInSeconds(getDate("week1 sun 01:00:00"));
        dummyLink->waitForMessages(4, _timeout);
        ASSERT_COMMAND_COUNT(4, *dummyLink);
    }
}

} // storage

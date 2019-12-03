// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/bucketdb/bucketmanager.h>
#include <vespa/storage/storageserver/bucketintegritychecker.h>
#include <vespa/storageapi/message/persistence.h>
#include <tests/common/testhelper.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/vespalib/io/fileutil.h>
#include <tests/common/teststorageapp.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/time.h>
#include <thread>

using namespace ::testing;

namespace storage {

struct BucketIntegrityCheckerTest : public Test {
    std::unique_ptr<vdstestlib::DirConfig> _config;
    std::unique_ptr<TestServiceLayerApp> _node;
    int _timeout; // Timeout in seconds before aborting

    void SetUp() override {
        _timeout = 60*2;
        _config = std::make_unique<vdstestlib::DirConfig>(getStandardConfig(true));
        _node = std::make_unique<TestServiceLayerApp>(
                DiskCount(256), NodeIndex(0), _config->getConfigId());
    }
};

TEST_F(BucketIntegrityCheckerTest, config) {
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
    EXPECT_EQ(60u, opt._dailyCycleStart);
    EXPECT_EQ(360u, opt._dailyCycleStop);
    EXPECT_EQ(SchedulingOptions::CONTINUE,  opt._dailyStates[0]);
    EXPECT_EQ(SchedulingOptions::RUN_CHEAP, opt._dailyStates[1]);
    EXPECT_EQ(SchedulingOptions::RUN_FULL,  opt._dailyStates[2]);
    EXPECT_EQ(SchedulingOptions::CONTINUE,  opt._dailyStates[3]);
    EXPECT_EQ(SchedulingOptions::DONT_RUN,  opt._dailyStates[4]);
    EXPECT_EQ(SchedulingOptions::RUN_CHEAP, opt._dailyStates[5]);
    EXPECT_EQ(SchedulingOptions::CONTINUE,  opt._dailyStates[6]);
    EXPECT_EQ(2u, opt._maxPendingCount);
    EXPECT_EQ(framework::SecondTime(7200), opt._minCycleTime);
    EXPECT_EQ(framework::SecondTime(5), opt._requestDelay);
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
        assert(gmtime_r(&startTime, &mytime));
        while (mytime.tm_wday != 0) {
            ++mytime.tm_mday;
            startTime = timegm(&mytime);
            assert(gmtime_r(&startTime, &mytime));
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

#define ASSERT_COMMAND_COUNT(count, dummylink) \
    { \
        std::ostringstream msgost; \
        if ((dummylink).getNumCommands() != count) { \
            for (uint32_t ijx=0; ijx<(dummylink).getNumCommands(); ++ijx) { \
                msgost << (dummylink).getCommand(ijx)->toString(true) << "\n"; \
            } \
        } \
        ASSERT_EQ(size_t(count), (dummylink).getNumCommands()) << msgost.str(); \
    }

TEST_F(BucketIntegrityCheckerTest, basic_functionality) {
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
        std::this_thread::sleep_for(10ms); // Give next message chance to come
        ASSERT_COMMAND_COUNT(0, *dummyLink);
        topLink.doneInit();
        checker.bump();
        // Should have started new run with 2 pending per disk
        dummyLink->waitForMessages(4, _timeout);
        std::this_thread::sleep_for(10ms); // Give 5th message chance to come
        ASSERT_COMMAND_COUNT(4, *dummyLink);
        auto* cmd1 = dynamic_cast<RepairBucketCommand*>(dummyLink->getCommand(0).get());
        EXPECT_EQ(230, cmd1->getPriority());
        ASSERT_TRUE(cmd1);
        EXPECT_EQ(document::BucketId(16, 0x234), cmd1->getBucketId());
        auto* cmd2 = dynamic_cast<RepairBucketCommand*>(dummyLink->getCommand(1).get());
        ASSERT_TRUE(cmd2);
        EXPECT_EQ(document::BucketId(16, 0x456), cmd2->getBucketId());
        auto* cmd3 = dynamic_cast<RepairBucketCommand*>(dummyLink->getCommand(2).get());
        ASSERT_TRUE(cmd3);
        EXPECT_EQ(document::BucketId(16, 0x567), cmd3->getBucketId());
        auto* cmd4 = dynamic_cast<RepairBucketCommand*>(dummyLink->getCommand(3).get());
        ASSERT_TRUE(cmd4);
        EXPECT_EQ(document::BucketId(16, 0x987), cmd4->getBucketId());

        // Answering a message on disk with no more buckets does not trigger new
        auto reply1 = std::make_shared<RepairBucketReply>(*cmd3);
        ASSERT_TRUE(checker.onUp(reply1));
        std::this_thread::sleep_for(10ms); // Give next message chance to come
        ASSERT_COMMAND_COUNT(4, *dummyLink);
        // Answering a message on disk with more buckets trigger new repair
        auto reply2 = std::make_shared<RepairBucketReply>(*cmd2);
        ASSERT_TRUE(checker.onUp(reply2));
        dummyLink->waitForMessages(5, _timeout);
        std::this_thread::sleep_for(10ms); // Give 6th message chance to come
        ASSERT_COMMAND_COUNT(5, *dummyLink);
        auto* cmd5 = dynamic_cast<RepairBucketCommand*>(dummyLink->getCommand(4).get());
        ASSERT_TRUE(cmd5);
        EXPECT_EQ(document::BucketId(16, 0x345), cmd5->getBucketId());
        // Fail a repair, causing it to be resent later, but first continue
        // with other bucket.
        auto reply3 = std::make_shared<RepairBucketReply>(*cmd1);
        reply3->setResult(api::ReturnCode(api::ReturnCode::IGNORED));
        ASSERT_TRUE(checker.onUp(reply3));
        dummyLink->waitForMessages(6, _timeout);
        std::this_thread::sleep_for(10ms); // Give 7th message chance to come
        ASSERT_COMMAND_COUNT(6, *dummyLink);
        auto* cmd6 = dynamic_cast<RepairBucketCommand*>(dummyLink->getCommand(5).get());
        ASSERT_TRUE(cmd6);
        EXPECT_EQ(document::BucketId(16, 0x123), cmd6->getBucketId());
        // Fail a repair with not found. That is an acceptable return code.
        // (No more requests as this was last for that disk)
        auto reply4 = std::make_shared<RepairBucketReply>(*cmd4);
        reply3->setResult(api::ReturnCode(api::ReturnCode::BUCKET_NOT_FOUND));
        ASSERT_TRUE(checker.onUp(reply4));
        std::this_thread::sleep_for(10ms); // Give 7th message chance to come
        ASSERT_COMMAND_COUNT(6, *dummyLink);

        // Send a repair reply that actually have corrected the bucket.
        api::BucketInfo newInfo(0x3456, 4, 8192);
        auto reply5 = std::make_shared<RepairBucketReply>(*cmd5, newInfo);
        reply5->setAltered(true);
        ASSERT_TRUE(checker.onUp(reply5));

        // Finish run. New iteration should not start yet as min
        // cycle time has not passed
        auto reply6 = std::make_shared<RepairBucketReply>(*cmd6);
        ASSERT_TRUE(checker.onUp(reply6));
        dummyLink->waitForMessages(7, _timeout);
        ASSERT_COMMAND_COUNT(7, *dummyLink);
        auto* cmd7 = dynamic_cast<RepairBucketCommand*>(dummyLink->getCommand(6).get());
        ASSERT_TRUE(cmd7);
        EXPECT_EQ(document::BucketId(16, 0x234), cmd7->getBucketId());
        auto reply7 = std::make_shared<RepairBucketReply>(*cmd7);
        ASSERT_TRUE(checker.onUp(reply7));
        std::this_thread::sleep_for(10ms); // Give 8th message chance to come
        ASSERT_COMMAND_COUNT(7, *dummyLink);

        // Still not time for next iteration
        dummyLink->reset();
        _node->getClock().setAbsoluteTimeInSeconds(getDate("week1 sun 00:59:59"));
        std::this_thread::sleep_for(10ms); // Give new run chance to start
        ASSERT_COMMAND_COUNT(0, *dummyLink);

        // Pass time until next cycle should start
        dummyLink->reset();
        _node->getClock().setAbsoluteTimeInSeconds(getDate("week1 sun 01:00:00"));
        dummyLink->waitForMessages(4, _timeout);
        ASSERT_COMMAND_COUNT(4, *dummyLink);
    }
}

} // storage

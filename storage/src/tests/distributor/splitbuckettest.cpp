// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/base/documentid.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/operation_sequencer.h>
#include <vespa/storage/distributor/operations/idealstate/splitoperation.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/persistence.h>
#include <tests/common/dummystoragelink.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/gtest/gtest.h>
#include "dummy_cluster_context.h"

using document::test::makeDocumentBucket;
using namespace document;
using namespace ::testing;

namespace storage::distributor {

struct SplitOperationTest : Test, DistributorTestUtil {
    uint32_t splitByteSize;
    uint32_t tooLargeBucketSize;
    uint32_t splitCount;
    uint32_t maxSplitBits;

    SplitOperationTest();

    void SetUp() override {
        createLinks();
        getConfig().setSplitCount(splitCount);
        getConfig().setSplitSize(splitByteSize);

    }

    void TearDown() override {
        close();
    }
};

SplitOperationTest::SplitOperationTest()
    : splitByteSize(10_Mi),
      tooLargeBucketSize(splitByteSize * 1.1),
      splitCount(UINT32_MAX),
      maxSplitBits(58)
{
}

namespace {
    api::StorageMessageAddress _Storage0Address(dummy_cluster_context.cluster_name_ptr(), lib::NodeType::STORAGE, 0);
}

TEST_F(SplitOperationTest, simple) {
    enableDistributorClusterState("distributor:1 storage:1");

    insertBucketInfo(document::BucketId(16, 1), 0, 0xabc, 1000,
                     tooLargeBucketSize, 250);

    SplitOperation op(dummy_cluster_context,
                      BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(0)),
                      maxSplitBits,
                      splitCount,
                      splitByteSize);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    {
        ASSERT_EQ(1, _sender.commands().size());

        std::shared_ptr<api::StorageCommand> msg  = _sender.command(0);
        ASSERT_EQ(msg->getType(), api::MessageType::SPLITBUCKET);
        EXPECT_EQ(_Storage0Address.toString(),
                  msg->getAddress()->toString());

        std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
        auto* sreply = static_cast<api::SplitBucketReply*>(reply.get());

        sreply->getSplitInfo().emplace_back(document::BucketId(17, 1),
                                            api::BucketInfo(100, 600, 5000000));

        sreply->getSplitInfo().emplace_back(document::BucketId(17, 0x10001),
                                            api::BucketInfo(110, 400, 6000000));

        op.receive(_sender, reply);
    }

    ASSERT_FALSE(getBucket(document::BucketId(16, 1)).valid());

    {
        BucketDatabase::Entry entry = getBucket(document::BucketId(17, 1));

        ASSERT_TRUE(entry.valid());
        ASSERT_EQ(1, entry->getNodeCount());
        EXPECT_EQ(0, entry->getNodeRef(0).getNode());
        EXPECT_EQ(100, entry->getNodeRef(0).getChecksum());
        EXPECT_EQ(5000000, entry->getNodeRef(0).getTotalDocumentSize());
        EXPECT_EQ(600, entry->getNodeRef(0).getDocumentCount());
    }

    {
        BucketDatabase::Entry entry(getBucket(document::BucketId(17, 0x10001)));

        ASSERT_TRUE(entry.valid());
        ASSERT_EQ(1, entry->getNodeCount());
        EXPECT_EQ(0, entry->getNodeRef(0).getNode());
        EXPECT_EQ(110, entry->getNodeRef(0).getChecksum());
        EXPECT_EQ(6000000, entry->getNodeRef(0).getTotalDocumentSize());
        EXPECT_EQ(400, entry->getNodeRef(0).getDocumentCount());
    }
}

TEST_F(SplitOperationTest, multi_node_failure) {
    {
        BucketDatabase::Entry entry(document::BucketId(16, 1));

        BucketCopy copy(0, 0, api::BucketInfo(250, 1000, tooLargeBucketSize));
        entry->addNode(copy, toVector<uint16_t>(0));

        entry->addNode(BucketCopy(0, 1, copy.getBucketInfo()),
                       toVector<uint16_t>(0));
        getBucketDatabase().update(entry);
    }

    enableDistributorClusterState("distributor:1 storage:2");

    SplitOperation op(dummy_cluster_context,
                      BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(0,1)),
                      maxSplitBits,
                      splitCount,
                      splitByteSize);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    {
        ASSERT_EQ(2, _sender.commands().size());

        {
            std::shared_ptr<api::StorageCommand> msg  = _sender.command(0);
            ASSERT_EQ(msg->getType(), api::MessageType::SPLITBUCKET);
            EXPECT_EQ(_Storage0Address.toString(),
                      msg->getAddress()->toString());

            auto* sreply = static_cast<api::SplitBucketReply*>(msg->makeReply().release());
            sreply->setResult(api::ReturnCode::OK);

            sreply->getSplitInfo().emplace_back(document::BucketId(17, 1),
                                                api::BucketInfo(100, 600, 5000000));

            sreply->getSplitInfo().emplace_back(document::BucketId(17, 0x10001),
                                                api::BucketInfo(110, 400, 6000000));

            op.receive(_sender, std::shared_ptr<api::StorageReply>(sreply));
        }

        sendReply(op, 1, api::ReturnCode::NOT_CONNECTED);
    }

    {
        BucketDatabase::Entry entry = getBucket(document::BucketId(16, 1));

        ASSERT_TRUE(entry.valid());
        ASSERT_EQ(1, entry->getNodeCount());

        EXPECT_EQ(1, entry->getNodeRef(0).getNode());
        EXPECT_EQ(250, entry->getNodeRef(0).getChecksum());
        EXPECT_EQ(tooLargeBucketSize, entry->getNodeRef(0).getTotalDocumentSize());
        EXPECT_EQ(1000, entry->getNodeRef(0).getDocumentCount());
    }

    {
        BucketDatabase::Entry entry = getBucket(document::BucketId(17, 1));

        ASSERT_TRUE(entry.valid());
        ASSERT_EQ(1, entry->getNodeCount());

        EXPECT_EQ(0, entry->getNodeRef(0).getNode());
        EXPECT_EQ(100, entry->getNodeRef(0).getChecksum());
        EXPECT_EQ(5000000, entry->getNodeRef(0).getTotalDocumentSize());
        EXPECT_EQ(600, entry->getNodeRef(0).getDocumentCount());
    }

    {
        BucketDatabase::Entry entry(getBucket(document::BucketId(17, 0x10001)));

        ASSERT_TRUE(entry.valid());
        ASSERT_EQ(1, entry->getNodeCount());

        EXPECT_EQ(0, entry->getNodeRef(0).getNode());
        EXPECT_EQ(110, entry->getNodeRef(0).getChecksum());
        EXPECT_EQ(6000000, entry->getNodeRef(0).getTotalDocumentSize());
        EXPECT_EQ(400, entry->getNodeRef(0).getDocumentCount());
    }
}

TEST_F(SplitOperationTest, copy_trusted_status_not_carried_over_after_split) {
    enableDistributorClusterState("distributor:1 storage:2");

    document::BucketId sourceBucket(16, 1);
    /*
     * Need 3 nodes to reproduce bug 6418516. Otherwise, the source bucket is
     * left with only 1 copy which implicitly becomes trusted. When this copy
     * is then split, the distributor db will automatically un-trust all buckets
     * since it sees that multiple copies are trusted that are not consistent
     * with each other. This prevents the bug from being visible.
     */
    addNodesToBucketDB(sourceBucket, "0=150/20/30000000/t,1=450/50/60000/u,"
                                     "2=550/60/70000");

    SplitOperation op(dummy_cluster_context,
                      BucketAndNodes(makeDocumentBucket(sourceBucket), toVector<uint16_t>(0, 1)),
                      maxSplitBits,
                      splitCount,
                      splitByteSize);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    ASSERT_EQ(3, _sender.commands().size());

    std::vector<document::BucketId> childBuckets;
    childBuckets.emplace_back(17, 1);
    childBuckets.emplace_back(17, 0x10001);

    // Note: only 2 out of 3 requests replied to!
    for (int i = 0; i < 2; ++i) {
        std::shared_ptr<api::StorageCommand> msg  = _sender.command(i);
        ASSERT_EQ(msg->getType(), api::MessageType::SPLITBUCKET);
        std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
        auto* sreply = static_cast<api::SplitBucketReply*>(reply.get());

        // Make sure copies differ so they cannot become implicitly trusted.
        sreply->getSplitInfo().emplace_back(childBuckets[0],
                                            api::BucketInfo(100 + i, 600, 5000000));
        sreply->getSplitInfo().emplace_back(childBuckets[1],
                                            api::BucketInfo(110 + i, 400, 6000000));

        op.receive(_sender, reply);
    }

    ASSERT_TRUE(getBucket(sourceBucket).valid()); // Still alive

    for (uint32_t i = 0; i < 2; ++i) {
        BucketDatabase::Entry entry(getBucket(childBuckets[i]));

        ASSERT_TRUE(entry.valid());
        ASSERT_EQ(2, entry->getNodes().size());

        for (uint16_t j = 0; j < 2; ++j) {
            EXPECT_FALSE(entry->getNodeRef(i).trusted());
        }
    }
}

TEST_F(SplitOperationTest, operation_blocked_by_pending_join) {
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg);
    OperationSequencer op_seq;

    enableDistributorClusterState("distributor:1 storage:2");

    document::BucketId joinTarget(2, 1);
    std::vector<document::BucketId> joinSources = {
        document::BucketId(3, 1), document::BucketId(3, 5)
    };
    auto joinCmd = std::make_shared<api::JoinBucketsCommand>(makeDocumentBucket(joinTarget));
    joinCmd->getSourceBuckets() = joinSources;
    joinCmd->setAddress(_Storage0Address);

    tracker.insert(joinCmd);

    insertBucketInfo(joinTarget, 0, 0xabc, 1000, 1234, true);

    SplitOperation op(dummy_cluster_context,
                      BucketAndNodes(makeDocumentBucket(joinTarget), toVector<uint16_t>(0)),
                      maxSplitBits,
                      splitCount,
                      splitByteSize);

    EXPECT_TRUE(op.isBlocked(tracker, op_seq));

    // Now, pretend there's a join for another node in the same bucket. This
    // will happen when a join is partially completed.
    tracker.clearMessagesForNode(0);
    EXPECT_FALSE(op.isBlocked(tracker, op_seq));

    joinCmd->setAddress(api::StorageMessageAddress::create(dummy_cluster_context.cluster_name_ptr(),
                                                           lib::NodeType::STORAGE, 1));
    tracker.insert(joinCmd);

    EXPECT_TRUE(op.isBlocked(tracker, op_seq));
}

TEST_F(SplitOperationTest, split_is_blocked_by_locked_bucket) {
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg);
    OperationSequencer op_seq;

    enableDistributorClusterState("distributor:1 storage:2");

    document::BucketId source_bucket(16, 1);
    insertBucketInfo(source_bucket, 0, 0xabc, 1000, tooLargeBucketSize, 250);

    SplitOperation op(dummy_cluster_context, BucketAndNodes(makeDocumentBucket(source_bucket), toVector<uint16_t>(0)),
                      maxSplitBits, splitCount, splitByteSize);

    EXPECT_FALSE(op.isBlocked(tracker, op_seq));
    auto token = op_seq.try_acquire(makeDocumentBucket(source_bucket), "foo");
    EXPECT_TRUE(token.valid());
    EXPECT_TRUE(op.isBlocked(tracker, op_seq));
}

} // storage::distributor

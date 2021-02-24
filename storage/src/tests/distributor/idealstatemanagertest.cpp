// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <tests/common/dummystoragelink.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/bucketdbupdater.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/operations/idealstate/mergeoperation.h>
#include <vespa/storage/distributor/operation_sequencer.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/vespalib/gtest/gtest.h>
#include "dummy_cluster_context.h"

using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;
using document::FixedBucketSpaces;
using namespace ::testing;

namespace storage::distributor {

struct IdealStateManagerTest : Test, DistributorTestUtil {
    IdealStateManagerTest()
        : Test(),
          DistributorTestUtil(),
          _bucketSpaces()
    {}
    void SetUp() override {
        createLinks();
        _bucketSpaces = getBucketSpaces();
    };

    void TearDown() override {
        close();
    }

    void setSystemState(const lib::ClusterState& systemState) {
        _distributor->enableClusterStateBundle(lib::ClusterStateBundle(systemState));
    }

    bool checkBlock(const IdealStateOperation& op,
                    const document::Bucket& bucket,
                    const PendingMessageTracker& tracker,
                    const OperationSequencer& op_seq) const
    {
        return op.checkBlock(bucket, tracker, op_seq);
    }

    bool checkBlockForAllNodes(const IdealStateOperation& op,
                               const document::Bucket& bucket,
                               const PendingMessageTracker& tracker,
                               const OperationSequencer& op_seq) const
    {
        return op.checkBlockForAllNodes(bucket, tracker, op_seq);
    }

    std::vector<document::BucketSpace> _bucketSpaces;
    std::string makeBucketStatusString(const std::string &defaultSpaceBucketStatus);
};

TEST_F(IdealStateManagerTest, sibling) {
    EXPECT_EQ(document::BucketId(1,1),
              getIdealStateManager().getDistributorComponent()
              .getSibling(document::BucketId(1, 0)));
    EXPECT_EQ(document::BucketId(1,0),
              getIdealStateManager().getDistributorComponent()
              .getSibling(document::BucketId(1, 1)));
    EXPECT_EQ(document::BucketId(2,3),
              getIdealStateManager().getDistributorComponent()
              .getSibling(document::BucketId(2, 1)));
    EXPECT_EQ(document::BucketId(2,1),
              getIdealStateManager().getDistributorComponent()
              .getSibling(document::BucketId(2, 3)));
}

TEST_F(IdealStateManagerTest, status_page) {
    close();
    getDirConfig().getConfig("stor-distributormanager").set("splitsize", "100");
    getDirConfig().getConfig("stor-distributormanager").set("splitcount", "1000000");
    getDirConfig().getConfig("stor-distributormanager").set("joinsize", "0");
    getDirConfig().getConfig("stor-distributormanager").set("joincount", "0");
    createLinks();
    setupDistributor(1, 1, "distributor:1 storage:1");

    insertBucketInfo(document::BucketId(16, 5), 0, 0xff, 100, 200, true, true);
    insertBucketInfo(document::BucketId(16, 2), 0, 0xff, 10, 10, true, true);

    std::ostringstream ost;
    getIdealStateManager().getBucketStatus(ost);

    EXPECT_EQ(makeBucketStatusString("BucketId(0x4000000000000002) : [node(idx=0,crc=0xff,docs=10/10,bytes=10/10,trusted=true,active=true,ready=false)]<br>\n"
                                     "<b>BucketId(0x4000000000000005):</b> <i> : split: [Splitting bucket because its maximum size (200 b, 100 docs, 100 meta, 200 b total) is "
                                     "higher than the configured limit of (100, 1000000)]</i> [node(idx=0,crc=0xff,docs=100/100,bytes=200/200,trusted=true,"
                                     "active=true,ready=false)]<br>\n"),
              ost.str());
}

TEST_F(IdealStateManagerTest, disabled_state_checker) {
    setupDistributor(1, 1, "distributor:1 storage:1");

    getConfig().setSplitSize(100);
    getConfig().setSplitCount(1000000);
    getConfig().disableStateChecker("SplitBucket");

    insertBucketInfo(document::BucketId(16, 5), 0, 0xff, 100, 200, true, true);
    insertBucketInfo(document::BucketId(16, 2), 0, 0xff, 10, 10, true, true);

    std::ostringstream ost;
    getIdealStateManager().getBucketStatus(ost);

    EXPECT_EQ(makeBucketStatusString(
        "BucketId(0x4000000000000002) : [node(idx=0,crc=0xff,docs=10/10,bytes=10/10,trusted=true,active=true,ready=false)]<br>\n"
         "<b>BucketId(0x4000000000000005):</b> <i> : split: [Splitting bucket because its maximum size (200 b, 100 docs, 100 meta, 200 b total) is "
         "higher than the configured limit of (100, 1000000)]</i> [node(idx=0,crc=0xff,docs=100/100,bytes=200/200,trusted=true,"
         "active=true,ready=false)]<br>\n"),
         ost.str());

    tick();
    EXPECT_EQ("", _distributor->getActiveIdealStateOperations());

}

TEST_F(IdealStateManagerTest, clear_active_on_node_down) {
    setSystemState(lib::ClusterState("distributor:1 storage:3"));
    for (int i = 1; i < 4; i++) {
        insertBucketInfo(document::BucketId(16, i), 0, 0xff, 100, 200);
        insertBucketInfo(document::BucketId(16, i), 1, 0xffe, 1020, 2300);
        insertBucketInfo(document::BucketId(16, i), 2, 0xfff, 1030, 2400);
    }

    tick();

    // Start all three operations.
    for (uint32_t i = 0; i < 3; ++i) {
        tick();
    }

    // Node 2 gets activated for each bucket as it has the most documents.
    EXPECT_EQ("setbucketstate to [2] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)) (pri 100)\n"
              "setbucketstate to [2] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000002)) (pri 100)\n"
              "setbucketstate to [2] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000003)) (pri 100)\n",
              _distributor->getActiveIdealStateOperations());

    setSystemState(lib::ClusterState("distributor:1 storage:3 .2.s:d"));

    EXPECT_EQ("", _distributor->getActiveIdealStateOperations());
    EXPECT_EQ(0, _distributor->getPendingMessageTracker()
                 .getNodeInfo().getPendingCount(0));
}

TEST_F(IdealStateManagerTest, recheck_when_active) {
    for (uint32_t j = 0; j < 3; j++) {
        insertBucketInfo(document::BucketId(16, 1), j, 0xff - j, 100, 200);
    }

    setSystemState(lib::ClusterState("distributor:1 storage:3"));

    tick();

    EXPECT_EQ("setbucketstate to [0] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)) (pri 100)\n",
              _distributor->getActiveIdealStateOperations());

    tick();

    EXPECT_EQ("setbucketstate to [0] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)) (pri 100)\n",
              _distributor->getActiveIdealStateOperations());

    tick();

    EXPECT_EQ("setbucketstate to [0] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)) (pri 100)\n",
              _distributor->getActiveIdealStateOperations());
}

TEST_F(IdealStateManagerTest, block_ideal_state_ops_on_full_request_bucket_info) {

    setupDistributor(2, 10, "distributor:1 storage:2");

    framework::defaultimplementation::FakeClock clock;
    PendingMessageTracker tracker(_node->getComponentRegister());
    OperationSequencer op_seq;

    document::BucketId bid(16, 1234);
    std::vector<document::BucketId> buckets;

    // RequestBucketInfoCommand does not have a specific bucketid since it's
    // sent to the entire node. It will then use a null bucketid.
    {
        auto msg = std::make_shared<api::RequestBucketInfoCommand>(makeBucketSpace(), buckets);
        msg->setAddress(api::StorageMessageAddress::create(dummy_cluster_context.cluster_name_ptr(), lib::NodeType::STORAGE, 4));
        tracker.insert(msg);
    }

    {
        RemoveBucketOperation op(dummy_cluster_context,
                                 BucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(3, 4)));
        EXPECT_TRUE(op.isBlocked(tracker, op_seq));
    }

    {
        // Don't trigger on requests to other nodes.
        RemoveBucketOperation op(dummy_cluster_context,
                                 BucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(3, 5)));
        EXPECT_FALSE(op.isBlocked(tracker, op_seq));
    }

    // Don't block on null-bucket messages that aren't RequestBucketInfo.
    {
        auto msg = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "foo", "bar", "baz");
        msg->setAddress(api::StorageMessageAddress::create(dummy_cluster_context.cluster_name_ptr(), lib::NodeType::STORAGE, 7));
        tracker.insert(msg);
    }

    {
        RemoveBucketOperation op(dummy_cluster_context,
                                 BucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(7)));
        EXPECT_FALSE(op.isBlocked(tracker, op_seq));
    }
}

TEST_F(IdealStateManagerTest, block_check_for_all_operations_to_specific_bucket) {
    setupDistributor(2, 10, "distributor:1 storage:2");
    framework::defaultimplementation::FakeClock clock;
    PendingMessageTracker tracker(_node->getComponentRegister());
    OperationSequencer op_seq;
    document::BucketId bid(16, 1234);

    {
        auto msg = std::make_shared<api::JoinBucketsCommand>(makeDocumentBucket(bid));
        msg->setAddress(api::StorageMessageAddress::create(dummy_cluster_context.cluster_name_ptr(), lib::NodeType::STORAGE, 4));
        tracker.insert(msg);
    }
    {
        RemoveBucketOperation op(dummy_cluster_context,
                                 BucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(7)));
        // Not blocked for exact node match.
        EXPECT_FALSE(checkBlock(op, makeDocumentBucket(bid), tracker, op_seq));
        // But blocked for bucket match!
        EXPECT_TRUE(checkBlockForAllNodes(op, makeDocumentBucket(bid), tracker, op_seq));
    }
}

TEST_F(IdealStateManagerTest, block_operations_with_locked_buckets) {
    setupDistributor(2, 10, "distributor:1 storage:2");
    framework::defaultimplementation::FakeClock clock;
    PendingMessageTracker tracker(_node->getComponentRegister());
    OperationSequencer op_seq;
    const auto bucket = makeDocumentBucket(document::BucketId(16, 1234));

    {
        auto msg = std::make_shared<api::JoinBucketsCommand>(bucket);
        msg->setAddress(api::StorageMessageAddress::create(dummy_cluster_context.cluster_name_ptr(), lib::NodeType::STORAGE, 1));
        tracker.insert(msg);
    }
    auto token = op_seq.try_acquire(bucket, "foo");
    EXPECT_TRUE(token.valid());
    {
        RemoveBucketOperation op(dummy_cluster_context, BucketAndNodes(bucket, toVector<uint16_t>(0)));
        EXPECT_TRUE(checkBlock(op, bucket, tracker, op_seq));
        EXPECT_TRUE(checkBlockForAllNodes(op, bucket, tracker, op_seq));
    }
}

std::string
IdealStateManagerTest::makeBucketStatusString(const std::string &defaultSpaceBucketStatus)
{
    std::ostringstream ost;
    for (const auto &bucketSpace : _bucketSpaces) {
        ost << "<h2>" << FixedBucketSpaces::to_string(bucketSpace) << " - " << bucketSpace << "</h2>\n";
        if (bucketSpace == FixedBucketSpaces::default_space()) {
            ost << defaultSpaceBucketStatus;
        }
    }
    return ost.str();
}

} // storage::distributor

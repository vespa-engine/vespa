// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "dummy_cluster_context.h"
#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/top_level_bucket_db_updater.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/operation_sequencer.h>
#include <vespa/storage/distributor/operations/idealstate/mergeoperation.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;
using document::FixedBucketSpaces;
using namespace ::testing;

namespace storage::distributor {

struct IdealStateManagerTest : Test, DistributorStripeTestUtil {
    IdealStateManagerTest()
        : Test(),
          DistributorStripeTestUtil(),
          _bucketSpaces()
    {}
    void SetUp() override {
        createLinks();
        _bucketSpaces = getBucketSpaces();
    };

    void TearDown() override {
        close();
    }

    bool checkBlock(const IdealStateOperation& op,
                    const document::Bucket& bucket,
                    const DistributorStripeOperationContext& ctx,
                    const OperationSequencer& op_seq) const
    {
        return op.checkBlock(bucket, ctx, op_seq);
    }

    bool checkBlockForAllNodes(const IdealStateOperation& op,
                               const document::Bucket& bucket,
                               const DistributorStripeOperationContext& ctx,
                               const OperationSequencer& op_seq) const
    {
        return op.checkBlockForAllNodes(bucket, ctx, op_seq);
    }

    std::vector<document::BucketSpace> _bucketSpaces;
    std::string makeBucketStatusString(const std::string &defaultSpaceBucketStatus);
};

TEST_F(IdealStateManagerTest, sibling) {
    EXPECT_EQ(document::BucketId(1,1),
              getIdealStateManager().operation_context()
              .get_sibling(document::BucketId(1, 0)));
    EXPECT_EQ(document::BucketId(1,0),
              getIdealStateManager().operation_context()
              .get_sibling(document::BucketId(1, 1)));
    EXPECT_EQ(document::BucketId(2,3),
              getIdealStateManager().operation_context()
              .get_sibling(document::BucketId(2, 1)));
    EXPECT_EQ(document::BucketId(2,1),
              getIdealStateManager().operation_context()
              .get_sibling(document::BucketId(2, 3)));
}

TEST_F(IdealStateManagerTest, status_page) {
    close();
    getDirConfig().getConfig("stor-distributormanager").set("splitsize", "100");
    getDirConfig().getConfig("stor-distributormanager").set("splitcount", "1000000");
    getDirConfig().getConfig("stor-distributormanager").set("joinsize", "0");
    getDirConfig().getConfig("stor-distributormanager").set("joincount", "0");
    createLinks();
    setup_stripe(1, 1, "distributor:1 storage:1");

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
    setup_stripe(1, 1, "distributor:1 storage:1");

    auto cfg = make_config();
    cfg->setSplitSize(100);
    cfg->setSplitCount(1000000);
    cfg->disableStateChecker("SplitBucket");
    configure_stripe(cfg);

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
    EXPECT_EQ("", active_ideal_state_operations());

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
              active_ideal_state_operations());

    setSystemState(lib::ClusterState("distributor:1 storage:3 .2.s:d"));

    EXPECT_EQ("", active_ideal_state_operations());
    EXPECT_EQ(0, pending_message_tracker().getNodeInfo().getPendingCount(0));
}

TEST_F(IdealStateManagerTest, recheck_when_active) {
    for (uint32_t j = 0; j < 3; j++) {
        insertBucketInfo(document::BucketId(16, 1), j, 0xff - j, 100, 200);
    }

    setSystemState(lib::ClusterState("distributor:1 storage:3"));

    tick();

    EXPECT_EQ("setbucketstate to [0] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)) (pri 100)\n",
              active_ideal_state_operations());

    tick();

    EXPECT_EQ("setbucketstate to [0] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)) (pri 100)\n",
              active_ideal_state_operations());

    tick();

    EXPECT_EQ("setbucketstate to [0] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)) (pri 100)\n",
              active_ideal_state_operations());
}

/**
 * Don't schedule ideal state operations when there's a pending cluster state.
 * This subsumes the legacy behavior of blocking ideal state ops when there is a
 * zero-bucket RequestBucketInfoCommand pending towards a node (i.e. full bucket
 * info fetch).
 *
 * This is for two reasons:
 *  - Avoids race conditions where we change the bucket set concurrently with
 *    requesting bucket info.
 *  - Once we get updated bucket info it's likely that the set of ideal state ops
 *    to execute will change anyway, so it makes sense to wait until it's ready.
 */
TEST_F(IdealStateManagerTest, block_ideal_state_ops_when_pending_cluster_state_is_present) {

    setup_stripe(2, 10, "version:1 distributor:1 storage:1 .0.s:d");

    // Trigger a pending cluster state with bucket info requests towards 1 node
    simulate_set_pending_cluster_state("version:2 distributor:1 storage:1");

    OperationSequencer op_seq;
    document::BucketId bid(16, 1234);

    {
        RemoveBucketOperation op(dummy_cluster_context,
                                 BucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(3, 4)));
        EXPECT_TRUE(op.isBlocked(operation_context(), op_seq));
    }

    clear_pending_cluster_state_bundle();

    {
        RemoveBucketOperation op(dummy_cluster_context,
                                 BucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(7)));
        EXPECT_FALSE(op.isBlocked(operation_context(), op_seq));
    }
}

TEST_F(IdealStateManagerTest, block_check_for_all_operations_to_specific_bucket) {
    setup_stripe(2, 10, "distributor:1 storage:2");
    framework::defaultimplementation::FakeClock clock;
    OperationSequencer op_seq;
    document::BucketId bid(16, 1234);

    {
        auto msg = std::make_shared<api::JoinBucketsCommand>(makeDocumentBucket(bid));
        msg->setAddress(api::StorageMessageAddress::create(dummy_cluster_context.cluster_name_ptr(), lib::NodeType::STORAGE, 4));
        pending_message_tracker().insert(msg);
    }
    {
        // TODO we might not want this particular behavior for merge operations either
        MergeOperation op(BucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(2, 3)));
        // Not blocked for exact node match.
        EXPECT_FALSE(checkBlock(op, makeDocumentBucket(bid), operation_context(), op_seq));
        // But blocked for bucket match!
        EXPECT_TRUE(checkBlockForAllNodes(op, makeDocumentBucket(bid), operation_context(), op_seq));
    }
}

TEST_F(IdealStateManagerTest, block_operations_with_locked_buckets) {
    setup_stripe(2, 10, "distributor:1 storage:2");
    framework::defaultimplementation::FakeClock clock;
    OperationSequencer op_seq;
    const auto bucket = makeDocumentBucket(document::BucketId(16, 1234));

    {
        auto msg = std::make_shared<api::JoinBucketsCommand>(bucket);
        msg->setAddress(api::StorageMessageAddress::create(dummy_cluster_context.cluster_name_ptr(), lib::NodeType::STORAGE, 1));
        pending_message_tracker().insert(msg);
    }
    auto token = op_seq.try_acquire(bucket, "foo");
    EXPECT_TRUE(token.valid());
    {
        RemoveBucketOperation op(dummy_cluster_context, BucketAndNodes(bucket, toVector<uint16_t>(0)));
        EXPECT_TRUE(checkBlock(op, bucket, operation_context(), op_seq));
        EXPECT_TRUE(checkBlockForAllNodes(op, bucket, operation_context(), op_seq));
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

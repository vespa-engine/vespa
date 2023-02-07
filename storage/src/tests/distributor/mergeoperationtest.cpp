// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/top_level_bucket_db_updater.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/operation_sequencer.h>
#include <vespa/storage/distributor/operations/idealstate/mergeoperation.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <charconv>

using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;
using namespace ::testing;

namespace storage::distributor {

namespace {
vespalib::string _g_storage("storage");
}

struct MergeOperationTest : Test, DistributorStripeTestUtil {
    using Priority = storage::api::StorageMessage::Priority;
    OperationSequencer _operation_sequencer;

    void SetUp() override {
        createLinks();
        _sender.setPendingMessageTracker(pending_message_tracker());
        _sender.set_operation_sequencer(_operation_sequencer);
    }

    void TearDown() override {
        close();
    }

    std::shared_ptr<MergeOperation> setup_minimal_merge_op();
    std::shared_ptr<MergeOperation> setup_simple_merge_op(const std::vector<uint16_t>& nodes,
                                                          Priority merge_pri = 120);
    std::shared_ptr<MergeOperation> setup_simple_merge_op(Priority merge_pri = 120);
    void assert_simple_merge_bucket_command();
    void assert_simple_delete_bucket_command();
    MergeBucketMetricSet& get_merge_metrics();
};

std::shared_ptr<MergeOperation> 
MergeOperationTest::setup_minimal_merge_op()
{
    document::BucketId bucket_id(16, 1);
    auto op = std::make_shared<MergeOperation>(BucketAndNodes(makeDocumentBucket(bucket_id), {0, 1, 2}));
    op->setIdealStateManager(&getIdealStateManager());
    return op;
}

std::shared_ptr<MergeOperation> 
MergeOperationTest::setup_simple_merge_op(const std::vector<uint16_t>& nodes, Priority merge_pri)
{
    getClock().setAbsoluteTimeInSeconds(10);

    addNodesToBucketDB(document::BucketId(16, 1),
                       "0=10/1/1/t,"
                       "1=20/1/1,"
                       "2=10/1/1/t");

    enable_cluster_state("distributor:1 storage:3");

    auto op = std::make_shared<MergeOperation>(BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)), nodes));
    op->setIdealStateManager(&getIdealStateManager());
    op->setPriority(merge_pri);
    op->start(_sender, framework::MilliSecTime(0));
    return op;
}

std::shared_ptr<MergeOperation>
MergeOperationTest::setup_simple_merge_op(Priority merge_pri)
{
    return setup_simple_merge_op({0, 1, 2}, merge_pri);
}

void
MergeOperationTest::assert_simple_merge_bucket_command()
{
    ASSERT_EQ("MergeBucketCommand(BucketId(0x4000000000000001), to time 10000000, "
              "cluster state version: 0, nodes: [0, 2, 1 (source only)], chain: [], "
              "reasons to start: ) => 0",
              _sender.getLastCommand(true));
}

void
MergeOperationTest::assert_simple_delete_bucket_command()
{
    ASSERT_EQ("DeleteBucketCommand(BucketId(0x4000000000000001)) "
              "Reasons to start:  => 1",
              _sender.getLastCommand(true));
}

MergeBucketMetricSet&
MergeOperationTest::get_merge_metrics()
{
    return dynamic_cast<MergeBucketMetricSet&>(*getIdealStateManager().getMetrics().operations[IdealStateOperation::MERGE_BUCKET]);
}

TEST_F(MergeOperationTest, simple) {
    auto op = setup_simple_merge_op();
    ASSERT_NO_FATAL_FAILURE(assert_simple_merge_bucket_command());
    sendReply(*op);
    ASSERT_NO_FATAL_FAILURE(assert_simple_delete_bucket_command());
    EXPECT_EQ(0, get_merge_metrics().ok.getValue());
    sendReply(*op);
    EXPECT_EQ(1, get_merge_metrics().ok.getValue());
}

TEST_F(MergeOperationTest, fail_if_source_only_copies_changed) {
    auto op = setup_simple_merge_op();
    ASSERT_NO_FATAL_FAILURE(assert_simple_merge_bucket_command());
    {
        auto& cmd = dynamic_cast<api::MergeBucketCommand&>(*_sender.command(0));
        EXPECT_EQ(0, cmd.getSourceIndex());
    }

    // Source-only copy changed during merge
    addNodesToBucketDB(document::BucketId(16, 1),
                       "0=10/1/1/t,"
                       "1=40/1/1,"
                       "2=10/1/1/t");
    sendReply(*op);
    // Should not be a remove here!
    ASSERT_NO_FATAL_FAILURE(assert_simple_merge_bucket_command());
    EXPECT_FALSE(op->ok());
    EXPECT_EQ(1, get_merge_metrics().failed.getValue());
    EXPECT_EQ(1, get_merge_metrics().source_only_copy_changed.getValue());
}

TEST_F(MergeOperationTest, fail_if_delete_bucket_fails) {
    auto op = setup_simple_merge_op();
    ASSERT_NO_FATAL_FAILURE(assert_simple_merge_bucket_command());
    sendReply(*op);
    ASSERT_NO_FATAL_FAILURE(assert_simple_delete_bucket_command());
    sendReply(*op, -1, api::ReturnCode::ABORTED);
    EXPECT_EQ(1, get_merge_metrics().failed.getValue());
    EXPECT_EQ(1, get_merge_metrics().source_only_copy_delete_failed.getValue());
}

namespace {
std::string getNodeList(std::string state, uint32_t redundancy, std::string existing) {
    lib::Distribution distribution(
            lib::Distribution::getDefaultDistributionConfig(redundancy));
    lib::ClusterState clusterState(state);
    vespalib::StringTokenizer st(existing, ",");
    std::vector<BucketCopy> bucketDB(st.size());
    for (uint32_t i = 0; i < st.size(); i++) {
        std::string num = st[i];
        size_t pos = num.find('t');
        bool trusted = false;

        if (pos != std::string::npos) {
            num.erase(pos);
            trusted = true;
        }
        uint16_t node;
        [[maybe_unused]] auto [ptr, ec] = std::from_chars(num.data(), num.data() + num.size(), node);
        assert(ec == std::errc{});
        bucketDB[i] = BucketCopy(0, node, api::BucketInfo(1, 2, 3));
        bucketDB[i].setTrusted(trusted);
    }
    std::vector<MergeMetaData> nodes(st.size());
    for (uint32_t i = 0; i < st.size(); i++) {
        nodes[i] = MergeMetaData(bucketDB[i].getNode(), bucketDB[i]);
    }
    MergeLimiter limiter(16);
    MergeOperation::generateSortedNodeList(distribution, clusterState,
                                           document::BucketId(32, 1),
                                           limiter, nodes);
    std::ostringstream actual;
    for (uint32_t i = 0; i < nodes.size(); i++) {
        if (i != 0) {
            actual << ",";
        }
        actual << nodes[i]._nodeIndex;
        if (nodes[i]._sourceOnly) {
            actual << "s";
        }
    }
    return actual.str();
}
}

TEST_F(MergeOperationTest, generate_node_list) {
    // If this fails, the distribution has changed and the rest of the test will
    // likely fail
    ASSERT_EQ("3,5,7,6,8,0,9,2,1,4",
              getNodeList("storage:10", 10, "0,1,2,3,4,5,6,7,8,9"));

    // Nodes that are initializing should be treated as up
    EXPECT_EQ("3,5,7s,6s",
              getNodeList("storage:10 .3.s:i .5.s:i", 2, "7,6,3,5")); // Ideal: 3,5

    // Order is given by ideal state algorithm, not order of storagenodes in bucket db
    EXPECT_EQ("3,5,7",
              getNodeList("storage:10", 3, "3,7,5"));

    // Node not in ideal state will be used if not enough nodes in ideal state
    EXPECT_EQ("3,7,6",
              getNodeList("storage:10", 3, "3,7,6"));

    // Nodes not in ideal state will be included as source only after redundancy
    // is reached
    EXPECT_EQ("3,5,7,8s",
              getNodeList("storage:10", 3, "3,5,7,8"));

    // Need at least redundancy copies that are not source only
    EXPECT_EQ("3,5,8,9s",
              getNodeList("storage:10", 3, "3,5,8,9"));

    // Order is given by storagenodes in bucket db
    // when no nodes are in ideal state
    EXPECT_EQ("4,1,2",
              getNodeList("storage:10", 3, "4,1,2"));

    EXPECT_EQ("3,0s,1s,2s,4s,5s,6s,7s,8s,9s",
              getNodeList("storage:10", 1, "0,1,2,3,4,5,6,7,8,9"));
    EXPECT_EQ("3,5,0s,1s,2s,4s,6s,7s,8s,9s",
              getNodeList("storage:10", 2, "0,1,2,3,4,5,6,7,8,9"));
    EXPECT_EQ("3,5,7,0s,1s,2s,4s,6s,8s,9s",
              getNodeList("storage:10", 3, "0,1,2,3,4,5,6,7,8,9"));
    EXPECT_EQ("3,5,7,6,0s,1s,2s,4s,8s,9s",
              getNodeList("storage:10", 4, "0,1,2,3,4,5,6,7,8,9"));
    EXPECT_EQ("3,5,7,6,8,0s,1s,2s,4s,9s",
              getNodeList("storage:10", 5, "0,1,2,3,4,5,6,7,8,9"));
    EXPECT_EQ("3,5,7,6,8,0,1s,2s,4s,9s",
              getNodeList("storage:10", 6, "0,1,2,3,4,5,6,7,8,9"));
    EXPECT_EQ("3,5,7,6,8,0,9,1s,2s,4s",
              getNodeList("storage:10", 7, "0,1,2,3,4,5,6,7,8,9"));
    EXPECT_EQ("3,5,7,6,8,0,9,2,1s,4s",
              getNodeList("storage:10", 8, "0,1,2,3,4,5,6,7,8,9"));
    EXPECT_EQ("3,5,7,6,8,0,9,2,1,4s",
              getNodeList("storage:10", 9, "0,1,2,3,4,5,6,7,8,9"));
    EXPECT_EQ("3,5,7,6,8,0,9,2,1,4",
              getNodeList("storage:10", 10, "0,1,2,3,4,5,6,7,8,9"));
    EXPECT_EQ("3,9s,8s,7s,6s,5s,4s,2s,1s,0s",
              getNodeList("storage:10", 1, "9,8,7,6,5,4,3,2,1,0"));
    EXPECT_EQ("3,5,9s,8s,7s,6s,4s,2s,1s,0s",
              getNodeList("storage:10", 2, "9,8,7,6,5,4,3,2,1,0"));
    EXPECT_EQ("3,5,7,9s,8s,6s,4s,2s,1s,0s",
              getNodeList("storage:10", 3, "9,8,7,6,5,4,3,2,1,0"));
    EXPECT_EQ("3,5,7,6,9s,8s,4s,2s,1s,0s",
              getNodeList("storage:10", 4, "9,8,7,6,5,4,3,2,1,0"));
    EXPECT_EQ("3,5,7,6,8,9s,4s,2s,1s,0s",
              getNodeList("storage:10", 5, "9,8,7,6,5,4,3,2,1,0"));
    EXPECT_EQ("3,5,7,6,8,0,9s,4s,2s,1s",
              getNodeList("storage:10", 6, "9,8,7,6,5,4,3,2,1,0"));
    EXPECT_EQ("3,5,7,6,8,0,9,4s,2s,1s",
              getNodeList("storage:10", 7, "9,8,7,6,5,4,3,2,1,0"));
    EXPECT_EQ("3,5,7,6,8,0,9,2,4s,1s",
              getNodeList("storage:10", 8, "9,8,7,6,5,4,3,2,1,0"));
    EXPECT_EQ("3,5,7,6,8,0,9,2,1,4s",
              getNodeList("storage:10", 9, "9,8,7,6,5,4,3,2,1,0"));
    EXPECT_EQ("3,5,7,6,8,0,9,2,1,4",
              getNodeList("storage:10", 10, "9,8,7,6,5,4,3,2,1,0"));

    // Trusted copies can be source-only if they are in the non-ideal node set.
    EXPECT_EQ("3,5,7,6,8,0,9,1s,2s,4s",
              getNodeList("storage:10", 7, "0,1t,2t,3,4,5,6,7,8,9"));

    EXPECT_EQ("3,5,7,6,8,0,9,1s,2s,4s",
              getNodeList("storage:10", 7, "0,1,2t,3,4,5,6,7,8,9"));

    // Retired nodes are not in ideal state
    // Ideal: 5,7
    EXPECT_EQ("0,2,3s",
              getNodeList("storage:10 .3.s:r", 2, "0,2,3"));
    // Ideal: 5,7,6
    EXPECT_EQ("0,2,3",
              getNodeList("storage:10 .3.s:r", 3, "0,2,3"));
}

TEST_F(MergeOperationTest, do_not_remove_copies_with_pending_messages) {
    document::BucketId bucket(16, 1);

    getClock().setAbsoluteTimeInSeconds(10);
    enable_cluster_state("distributor:1 storage:3");
    addNodesToBucketDB(bucket,
                       "0=10/1/1/t,"
                       "1=20/1/1,"
                       "2=10/1/1/t");

    MergeOperation op(BucketAndNodes(makeDocumentBucket(bucket),
                                     toVector<uint16_t>(0, 1, 2)));
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    std::string merge("MergeBucketCommand(BucketId(0x4000000000000001), to time 10000000, "
                      "cluster state version: 0, nodes: [0, 2, 1 (source only)], chain: [], "
                      "reasons to start: ) => 0");

    ASSERT_EQ(merge, _sender.getLastCommand(true));

    // Suddenly a wild operation appears to the source only copy!
    // Removes are blocked by all and any operation types, so can just choose
    // at will.
    auto msg = std::make_shared<api::SetBucketStateCommand>(
            makeDocumentBucket(bucket), api::SetBucketStateCommand::ACTIVE);
    msg->setAddress(api::StorageMessageAddress::create(&_g_storage, lib::NodeType::STORAGE, 1));
    pending_message_tracker().insert(msg);

    sendReply(op);
    // Should not be a remove here!
    ASSERT_EQ(merge, _sender.getLastCommand(true));
    EXPECT_FALSE(op.ok());
    EXPECT_EQ(1, get_merge_metrics().failed.getValue());
    EXPECT_EQ(1, get_merge_metrics().source_only_copy_delete_blocked.getValue());
}

/*
 * We allow active source-only replicas to be deleted to prevent
 * "deadlocks" between the merge and bucket activation state checkers.
 *
 * Example deadlock scenario with explanations:
 * If the only trusted replica is in a non-ideal location, it will
 * be marked as active if it is also in ready state. The bucket activation
 * state checker prefers activating trusted ready replicas, so it
 * will not automatically activate an untrusted ideal location replica, even
 * if it's marked as ready. Trusted status of the ideal replicas will not
 * change even after a successful merge since the checksums between
 * regular and source-only replicas will usually not converge to the
 * same value. Consequently, we won't get rid of the non-ideal replica
 * unless either its content node or the distributor is restarted.
 *
 * Such a situation could arise if the ideal replicas are transiently
 * partitioned away and a new replica is created from feed load before
 * they return. The new replica would be marked as trusted & active, as the
 * distributor has lost all prior knowledge of the partitioned replicas.
 *
 * Deleting an active replica will lead to a transient loss of coverage
 * for the bucket (until an ideal replica can be activated), but this
 * should be an uncommon edge case and it's arguably better than to never
 * activate the ideal replicas at all.
 */
TEST_F(MergeOperationTest, allow_deleting_active_source_only_replica) {
    getClock().setAbsoluteTimeInSeconds(10);

    addNodesToBucketDB(document::BucketId(16, 1),
                       "0=10/1/1/t,"
                       "1=20/1/1/u/a,"
                       "2=10/1/1/t");

    enable_cluster_state("distributor:1 storage:3");
    MergeOperation op(BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(0, 1, 2)));
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    std::string merge(
            "MergeBucketCommand(BucketId(0x4000000000000001), to time "
            "10000000, cluster state version: 0, nodes: [0, 2, 1 "
            "(source only)], chain: [], reasons to start: ) => 0");
    ASSERT_EQ(merge, _sender.getLastCommand(true));

    sendReply(op);
    ASSERT_EQ("DeleteBucketCommand(BucketId(0x4000000000000001)) "
              "Reasons to start:  => 1",
              _sender.getLastCommand(true));
}

TEST_F(MergeOperationTest, mark_redundant_trusted_copies_as_source_only) {
    // This test uses the same distribution as testGenerateNodeList(), i.e.
    // an ideal state sequence of [3, 5, 7, 6, 8, 0, 9, 2, 1, 4]

    // 3 redundancy, 5 trusted -> 2 trusted source only.
    EXPECT_EQ("3,5,7,6s,8s",
              getNodeList("storage:10", 3, "3t,5t,7t,6t,8t"));

    // 3 redundancy, 4 trusted -> 1 trusted source only.
    EXPECT_EQ("3,5,7,6s,8s",
              getNodeList("storage:10", 3, "3t,5t,7t,6t,8"));

    // 3 redundancy, 3 trusted -> 0 trusted source only, 2 non-trusted sources.
    EXPECT_EQ("3,5,7,6s,8s",
              getNodeList("storage:10", 3, "3t,5t,7t,6,8"));

    // Trusted-ness should not be taken into account when marking nodes as source-only.
    // 2 out of 3 ideal replicas trusted.
    EXPECT_EQ("3,5,7,6s,8s",
              getNodeList("storage:10", 3, "3t,5t,7,6t,8t"));

    // 1 out of 3 ideal replicas trusted.
    EXPECT_EQ("3,5,7,6s,8s",
              getNodeList("storage:10", 3, "3t,5,7,6t,8t"));

    // 0 out of 3 ideal replicas trusted.
    EXPECT_EQ("3,5,7,6s,8s",
              getNodeList("storage:10", 3, "3,5,7,6t,8t"));

    // #redundancy of trusted, but none are ideal. Non-ideal trusted may be
    // marked as source only.
    EXPECT_EQ("3,5,7,6s,8s,0s,9s",
              getNodeList("storage:10", 3, "3,5,7,6,8t,0t,9t"));

    // Allow for removing excess trusted, non-ideal copies.
    EXPECT_EQ("3,5,7,6s,8s,0s,9s",
              getNodeList("storage:10", 3, "3,5,7,6t,8t,0t,9t"));
}

TEST_F(MergeOperationTest, only_mark_redundant_retired_replicas_as_source_only) {
    // No nodes in ideal state and all nodes are retired. With redundancy of 2
    // we can only mark the last replica in the DB as source-only. Retired
    // nodes are meant as source-only due to being migrated away from, but
    // source-only nodes will have their replica removed after a successful
    // merge, which we cannot allow to happen here.
    EXPECT_EQ("1,0,2s",
              getNodeList("storage:3 .0.s:r .1.s:r .2.s:r", 2, "1,0,2"));
}

TEST_F(MergeOperationTest, mark_post_merge_redundant_replicas_source_only) {
    // Ideal state sequence is [3, 5, 7, 6, 8, 0, 9, 2, 1, 4]

    // Retired node 7 is not part of the #redundancy ideal state and should be moved
    // to node 6. Once the merge is done we'll end up with too many replicas unless
    // we allow marking the to-be-moved replica as source only.
    EXPECT_EQ("3,5,6,7s",
              getNodeList("storage:10 .7.s:r", 3, "3t,5t,7t,6"));

    // Should be allowed to mark as source only even if retired replica is the
    // only trusted replica at the time the merge starts.
    EXPECT_EQ("3,5,6,7s",
              getNodeList("storage:10 .7.s:r", 3, "3,5,7t,6"));

    // This extends to multiple retired nodes.
    EXPECT_EQ("3,6,8,5s,7s",
              getNodeList("storage:10 .5.s:r .7.s:r", 3, "3t,5t,7t,6,8"));

    // If number of post-merge ideal nodes is lower than desired redundancy, don't
    // mark any as source only.
    EXPECT_EQ("3,5,7,6",
              getNodeList("storage:10", 5, "3,5,7,6"));

    // Same applies to when post-merge ideal nodes is _equal_ to desired redundancy.
    EXPECT_EQ("3,5,7,6",
              getNodeList("storage:10", 4, "3,5,7,6"));
}

TEST_F(MergeOperationTest, merge_operation_is_blocked_by_any_busy_target_node) {
    getClock().setAbsoluteTimeInSeconds(10);
    addNodesToBucketDB(document::BucketId(16, 1), "0=10/1/1/t,1=20/1/1,2=10/1/1/t");
    enable_cluster_state("distributor:1 storage:3");
    MergeOperation op(BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)), toVector<uint16_t>(0, 1, 2)));
    op.setIdealStateManager(&getIdealStateManager());

    // Should not block on nodes _not_ included in operation node set
    pending_message_tracker().getNodeInfo().setBusy(3, std::chrono::seconds(10));
    EXPECT_FALSE(op.isBlocked(operation_context(), _operation_sequencer));

    // Node 1 is included in operation node set and should cause a block
    pending_message_tracker().getNodeInfo().setBusy(0, std::chrono::seconds(10));
    EXPECT_TRUE(op.isBlocked(operation_context(), _operation_sequencer));

    getClock().addSecondsToTime(11);
    EXPECT_FALSE(op.isBlocked(operation_context(), _operation_sequencer)); // No longer busy

    // Should block on other operation nodes than the first listed as well
    pending_message_tracker().getNodeInfo().setBusy(1, std::chrono::seconds(10));
    EXPECT_TRUE(op.isBlocked(operation_context(), _operation_sequencer));
}


TEST_F(MergeOperationTest, global_bucket_merges_are_not_blocked_by_busy_nodes) {
    getClock().setAbsoluteTimeInSeconds(10);
    document::BucketId bucket_id(16, 1);
    addNodesToBucketDB(bucket_id, "0=10/1/1/t,1=20/1/1,2=10/1/1/t");
    enable_cluster_state("distributor:1 storage:3");
    document::Bucket global_bucket(document::FixedBucketSpaces::global_space(), bucket_id);
    MergeOperation op(BucketAndNodes(global_bucket, toVector<uint16_t>(0, 1, 2)));
    op.setIdealStateManager(&getIdealStateManager());

    // Node 1 is included in operation node set but should not cause a block of global bucket merge
    pending_message_tracker().getNodeInfo().setBusy(0, std::chrono::seconds(10));
    EXPECT_FALSE(op.isBlocked(operation_context(), _operation_sequencer));
}

TEST_F(MergeOperationTest, merge_operation_is_blocked_by_locked_bucket) {
    getClock().setAbsoluteTimeInSeconds(10);
    addNodesToBucketDB(document::BucketId(16, 1), "0=10/1/1/t,1=20/1/1,2=10/1/1/t");
    enable_cluster_state("distributor:1 storage:3");
    MergeOperation op(BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)), toVector<uint16_t>(0, 1, 2)));
    op.setIdealStateManager(&getIdealStateManager());

    EXPECT_FALSE(op.isBlocked(operation_context(), _operation_sequencer));
    auto token = _operation_sequencer.try_acquire(makeDocumentBucket(document::BucketId(16, 1)), "foo");
    EXPECT_TRUE(token.valid());
    EXPECT_TRUE(op.isBlocked(operation_context(), _operation_sequencer));
}

TEST_F(MergeOperationTest, missing_replica_is_included_in_limited_node_list) {
    setup_stripe(Redundancy(4), NodeCount(4), "distributor:1 storage:4");
    getClock().setAbsoluteTimeInSeconds(10);
    addNodesToBucketDB(document::BucketId(16, 1), "1=0/0/0/t,2=0/0/0/t,3=0/0/0/t");
    const uint16_t max_merge_size = 2;
    MergeOperation op(BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)), toVector<uint16_t>(0, 1, 2, 3)), max_merge_size);
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    // Must include missing node 0 and not just 2 existing replicas
    EXPECT_EQ("MergeBucketCommand(BucketId(0x4000000000000001), to time 10000000, "
              "cluster state version: 0, nodes: [0, 1], chain: [], "
              "reasons to start: ) => 0",
              _sender.getLastCommand(true));
}

TEST_F(MergeOperationTest, merge_operation_is_blocked_by_request_bucket_info_to_any_node_in_chain) {
    getClock().setAbsoluteTimeInSeconds(10);
    document::BucketId bucket_id(16, 1);
    addNodesToBucketDB(bucket_id, "0=10/1/1/t,1=20/1/1,2=10/1/1/t");
    enable_cluster_state("distributor:1 storage:3");
    MergeOperation op(BucketAndNodes(makeDocumentBucket(bucket_id), toVector<uint16_t>(0, 1, 2)));
    op.setIdealStateManager(&getIdealStateManager());

    // Not initially blocked
    EXPECT_FALSE(op.isBlocked(operation_context(), _operation_sequencer));

    auto info_cmd = std::make_shared<api::RequestBucketInfoCommand>(
            makeBucketSpace(), std::vector<document::BucketId>({bucket_id}));
    info_cmd->setAddress(api::StorageMessageAddress::create(&_g_storage, lib::NodeType::STORAGE, 1)); // 1 is in chain
    pending_message_tracker().insert(info_cmd);

    // Now blocked by info request
    EXPECT_TRUE(op.isBlocked(operation_context(), _operation_sequencer));
}

TEST_F(MergeOperationTest, merge_operation_is_not_blocked_by_request_bucket_info_to_unrelated_bucket) {
    getClock().setAbsoluteTimeInSeconds(10);
    document::BucketId bucket_id(16, 1);
    document::BucketId other_bucket_id(16, 2);
    addNodesToBucketDB(bucket_id, "0=10/1/1/t,1=20/1/1,2=10/1/1/t");
    enable_cluster_state("distributor:1 storage:3");
    MergeOperation op(BucketAndNodes(makeDocumentBucket(bucket_id), toVector<uint16_t>(0, 1, 2)));
    op.setIdealStateManager(&getIdealStateManager());

    auto info_cmd = std::make_shared<api::RequestBucketInfoCommand>(
            makeBucketSpace(), std::vector<document::BucketId>({other_bucket_id}));
    info_cmd->setAddress(api::StorageMessageAddress::create(&_g_storage, lib::NodeType::STORAGE, 1));
    pending_message_tracker().insert(info_cmd);

    // Not blocked; bucket info request is for another bucket
    EXPECT_FALSE(op.isBlocked(operation_context(), _operation_sequencer));
}

TEST_F(MergeOperationTest, on_blocked_updates_metrics)
{
    auto op = setup_minimal_merge_op();
    auto metrics = getIdealStateManager().getMetrics().operations[IdealStateOperation::MERGE_BUCKET];
    EXPECT_EQ(0, metrics->blocked.getValue());
    op->on_blocked();
    EXPECT_EQ(1, metrics->blocked.getValue());
}

TEST_F(MergeOperationTest, on_throttled_updates_metrics)
{
    auto op = setup_minimal_merge_op();
    auto metrics = getIdealStateManager().getMetrics().operations[IdealStateOperation::MERGE_BUCKET];
    EXPECT_EQ(0, metrics->throttled.getValue());
    op->on_throttled();
    EXPECT_EQ(1, metrics->throttled.getValue());
}

TEST_F(MergeOperationTest, unordered_merges_only_sent_iff_config_enabled_and_all_nodes_support_feature) {
    setup_stripe(Redundancy(4), NodeCount(4), "distributor:1 storage:4");
    NodeSupportedFeatures with_unordered;
    with_unordered.unordered_merge_chaining = true;

    set_node_supported_features(1, with_unordered);
    set_node_supported_features(2, with_unordered);

    auto config = make_config();
    config->set_use_unordered_merge_chaining(true);
    configure_stripe(std::move(config));

    // Only nodes {1, 2} support unordered merging; merges should be ordered (sent to lowest index node 1).
    setup_simple_merge_op({1, 2, 3}); // Note: these will be re-ordered in ideal state order internally
    ASSERT_EQ("MergeBucketCommand(BucketId(0x4000000000000001), to time 10000000, "
              "cluster state version: 0, nodes: [2, 1, 3], chain: [], "
              "reasons to start: ) => 1",
              _sender.getLastCommand(true));

    // All involved nodes support unordered merging; merges should be unordered (sent to ideal node 2)
    setup_simple_merge_op({1, 2});
    ASSERT_EQ("MergeBucketCommand(BucketId(0x4000000000000001), to time 10000001, "
              "cluster state version: 0, nodes: [2, 1], chain: [] (unordered forwarding), "
              "reasons to start: ) => 2",
              _sender.getLastCommand(true));

    _sender.clear();

    config = make_config();
    config->set_use_unordered_merge_chaining(false);
    configure_stripe(std::move(config));

    // If config is not enabled, should send ordered even if nodes support the feature.
    setup_simple_merge_op({2, 1});
    ASSERT_EQ("MergeBucketCommand(BucketId(0x4000000000000001), to time 10000002, "
              "cluster state version: 0, nodes: [2, 1], chain: [], "
              "reasons to start: ) => 1",
              _sender.getLastCommand(true));
}

TEST_F(MergeOperationTest, delete_bucket_inherits_merge_priority) {
    auto op = setup_simple_merge_op(Priority(125));
    ASSERT_NO_FATAL_FAILURE(assert_simple_merge_bucket_command());
    sendReply(*op);
    ASSERT_NO_FATAL_FAILURE(assert_simple_delete_bucket_command());
    auto del_cmd = std::dynamic_pointer_cast<api::DeleteBucketCommand>(_sender.commands().back());
    ASSERT_TRUE(del_cmd);
    EXPECT_EQ(int(del_cmd->getPriority()), int(op->getPriority()));
    EXPECT_EQ(int(del_cmd->getPriority()), 125);
}

// TODO less magical numbers, but the priority mapping is technically config...
TEST_F(MergeOperationTest, delete_bucket_priority_is_capped_to_feed_pri_120) {
    auto op = setup_simple_merge_op(Priority(119));
    ASSERT_NO_FATAL_FAILURE(assert_simple_merge_bucket_command());
    sendReply(*op);
    ASSERT_NO_FATAL_FAILURE(assert_simple_delete_bucket_command());
    auto del_cmd = std::dynamic_pointer_cast<api::DeleteBucketCommand>(_sender.commands().back());
    ASSERT_TRUE(del_cmd);
    EXPECT_EQ(int(del_cmd->getPriority()), 120);
}

} // storage::distributor

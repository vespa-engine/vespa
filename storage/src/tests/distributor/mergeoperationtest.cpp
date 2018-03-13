// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <boost/lexical_cast.hpp>
#include <cppunit/extensions/HelperMacros.h>
#include <iomanip>
#include <tests/common/dummystoragelink.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/operations/idealstate/mergeoperation.h>
#include <vespa/storage/distributor/bucketdbupdater.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/vespalib/text/stringtokenizer.h>

using std::shared_ptr;
using document::test::makeDocumentBucket;

namespace storage {
namespace distributor {

class MergeOperationTest : public CppUnit::TestFixture,
                           public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(MergeOperationTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testFailIfSourceOnlyCopiesChanged);
    CPPUNIT_TEST(testGenerateNodeList);
    CPPUNIT_TEST(doNotRemoveCopiesWithPendingMessages);
    CPPUNIT_TEST(allow_deleting_active_source_only_replica);
    CPPUNIT_TEST(testMarkRedundantTrustedCopiesAsSourceOnly);
    CPPUNIT_TEST(onlyMarkRedundantRetiredReplicasAsSourceOnly);
    CPPUNIT_TEST(mark_post_merge_redundant_replicas_source_only);
    CPPUNIT_TEST(merge_operation_is_blocked_by_any_busy_target_node);
    CPPUNIT_TEST(missing_replica_is_included_in_limited_node_list);
    CPPUNIT_TEST_SUITE_END();

    std::unique_ptr<PendingMessageTracker> _pendingTracker;

protected:
    void testSimple();
    void testFailIfSourceOnlyCopiesChanged();
    void testGenerateNodeList();
    void doNotRemoveCopiesWithPendingMessages();
    void allow_deleting_active_source_only_replica();
    void testMarkRedundantTrustedCopiesAsSourceOnly();
    void onlyMarkRedundantRetiredReplicasAsSourceOnly();
    void mark_post_merge_redundant_replicas_source_only();
    void merge_operation_is_blocked_by_any_busy_target_node();
    void missing_replica_is_included_in_limited_node_list();

public:
    void setUp() override {
        createLinks();
        _pendingTracker.reset(new PendingMessageTracker(getComponentRegister()));
        _sender.setPendingMessageTracker(*_pendingTracker);
    }

    void tearDown() override {
        close();
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(MergeOperationTest);

void
MergeOperationTest::testSimple()
{
    getClock().setAbsoluteTimeInSeconds(10);

    addNodesToBucketDB(document::BucketId(16, 1),
                       "0=10/1/1/t,"
                       "1=20/1/1,"
                       "2=10/1/1/t");

    enableDistributorClusterState("distributor:1 storage:3");

    MergeOperation op(BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(0, 1, 2)));
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "MergeBucketCommand(BucketId(0x4000000000000001), to time 10000000, "
                    "cluster state version: 0, nodes: [0, 2, 1 (source only)], chain: [], "
                    "reasons to start: ) => 0"),
            _sender.getLastCommand(true));

    sendReply(op);

    CPPUNIT_ASSERT_EQUAL(
            std::string("DeleteBucketCommand(BucketId(0x4000000000000001)) "
                        "Reasons to start:  => 1"),
            _sender.getLastCommand(true));

}

void
MergeOperationTest::testFailIfSourceOnlyCopiesChanged()
{
    getClock().setAbsoluteTimeInSeconds(10);

    addNodesToBucketDB(document::BucketId(16, 1),
                       "0=10/1/1/t,"
                       "1=20/1/1,"
                       "2=10/1/1/t");

    enableDistributorClusterState("distributor:1 storage:3");

    MergeOperation op(BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(0, 1, 2)));
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    std::string merge("MergeBucketCommand(BucketId(0x4000000000000001), to time 10000000, "
                      "cluster state version: 0, nodes: [0, 2, 1 (source only)], chain: [], "
                      "reasons to start: ) => 0");

    CPPUNIT_ASSERT_EQUAL(merge, _sender.getLastCommand(true));
    {
        const api::MergeBucketCommand& cmd(
                dynamic_cast<api::MergeBucketCommand&>(*_sender.commands[0]));
        CPPUNIT_ASSERT_EQUAL(uint16_t(0), cmd.getSourceIndex());
    }

    // Source-only copy changed during merge
    addNodesToBucketDB(document::BucketId(16, 1),
                       "0=10/1/1/t,"
                       "1=40/1/1,"
                       "2=10/1/1/t");
    sendReply(op);
    // Should not be a remove here!
    CPPUNIT_ASSERT_EQUAL(merge, _sender.getLastCommand(true));
    CPPUNIT_ASSERT(!op.ok());
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
        bucketDB[i] = BucketCopy(0, atoi(num.c_str()),
                                 api::BucketInfo(1, 2, 3));
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

void
MergeOperationTest::testGenerateNodeList()
{
    // If this fails, the distribution has changed and the rest of the test will
    // likely fail
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,9,2,1,4"),
            getNodeList("storage:10", 10, "0,1,2,3,4,5,6,7,8,9"));

    // Nodes that are initializing should be treated as up
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7s,6s"),
            getNodeList("storage:10 .3.s:i .5.s:i", 2, "7,6,3,5")); // Ideal: 3,5

    // Order is given by ideal state algorithm, not order of storagenodes in bucket db
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7"),
            getNodeList("storage:10", 3, "3,7,5"));

    // Node not in ideal state will be used if not enough nodes in ideal state
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,7,6"),
            getNodeList("storage:10", 3, "3,7,6"));

    // Nodes not in ideal state will be included as source only after redundancy
    // is reached
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,8s"),
            getNodeList("storage:10", 3, "3,5,7,8"));

    // Need at least redundancy copies that are not source only
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,8,9s"),
            getNodeList("storage:10", 3, "3,5,8,9"));

    // Order is given by storagenodes in bucket db
    // when no nodes are in ideal state
    CPPUNIT_ASSERT_EQUAL(
            std::string("4,1,2"),
            getNodeList("storage:10", 3, "4,1,2"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("3,0s,1s,2s,4s,5s,6s,7s,8s,9s"),
            getNodeList("storage:10", 1, "0,1,2,3,4,5,6,7,8,9"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,0s,1s,2s,4s,6s,7s,8s,9s"),
            getNodeList("storage:10", 2, "0,1,2,3,4,5,6,7,8,9"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,0s,1s,2s,4s,6s,8s,9s"),
            getNodeList("storage:10", 3, "0,1,2,3,4,5,6,7,8,9"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,0s,1s,2s,4s,8s,9s"),
            getNodeList("storage:10", 4, "0,1,2,3,4,5,6,7,8,9"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0s,1s,2s,4s,9s"),
            getNodeList("storage:10", 5, "0,1,2,3,4,5,6,7,8,9"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,1s,2s,4s,9s"),
            getNodeList("storage:10", 6, "0,1,2,3,4,5,6,7,8,9"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,9,1s,2s,4s"),
            getNodeList("storage:10", 7, "0,1,2,3,4,5,6,7,8,9"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,9,2,1s,4s"),
            getNodeList("storage:10", 8, "0,1,2,3,4,5,6,7,8,9"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,9,2,1,4s"),
            getNodeList("storage:10", 9, "0,1,2,3,4,5,6,7,8,9"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,9,2,1,4"),
            getNodeList("storage:10", 10, "0,1,2,3,4,5,6,7,8,9"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,9s,8s,7s,6s,5s,4s,2s,1s,0s"),
            getNodeList("storage:10", 1, "9,8,7,6,5,4,3,2,1,0"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,9s,8s,7s,6s,4s,2s,1s,0s"),
            getNodeList("storage:10", 2, "9,8,7,6,5,4,3,2,1,0"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,9s,8s,6s,4s,2s,1s,0s"),
            getNodeList("storage:10", 3, "9,8,7,6,5,4,3,2,1,0"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,9s,8s,4s,2s,1s,0s"),
            getNodeList("storage:10", 4, "9,8,7,6,5,4,3,2,1,0"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,9s,4s,2s,1s,0s"),
            getNodeList("storage:10", 5, "9,8,7,6,5,4,3,2,1,0"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,9s,4s,2s,1s"),
            getNodeList("storage:10", 6, "9,8,7,6,5,4,3,2,1,0"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,9,4s,2s,1s"),
            getNodeList("storage:10", 7, "9,8,7,6,5,4,3,2,1,0"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,9,2,4s,1s"),
            getNodeList("storage:10", 8, "9,8,7,6,5,4,3,2,1,0"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,9,2,1,4s"),
            getNodeList("storage:10", 9, "9,8,7,6,5,4,3,2,1,0"));
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,9,2,1,4"),
            getNodeList("storage:10", 10, "9,8,7,6,5,4,3,2,1,0"));

    // Trusted copies can be source-only if they are in the non-ideal node set.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,9,1s,2s,4s"),
            getNodeList("storage:10", 7, "0,1t,2t,3,4,5,6,7,8,9"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6,8,0,9,1s,2s,4s"),
            getNodeList("storage:10", 7, "0,1,2t,3,4,5,6,7,8,9"));

    // Retired nodes are not in ideal state
    // Ideal: 5,7
    CPPUNIT_ASSERT_EQUAL(
            std::string("0,2,3s"),
            getNodeList("storage:10 .3.s:r", 2, "0,2,3"));
    // Ideal: 5,7,6
    CPPUNIT_ASSERT_EQUAL(
            std::string("0,2,3"),
            getNodeList("storage:10 .3.s:r", 3, "0,2,3"));
}

void
MergeOperationTest::doNotRemoveCopiesWithPendingMessages() {
    document::BucketId bucket(16, 1);

    getClock().setAbsoluteTimeInSeconds(10);
    enableDistributorClusterState("distributor:1 storage:3");
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

    CPPUNIT_ASSERT_EQUAL(merge, _sender.getLastCommand(true));

    // Suddenly a wild operation appears to the source only copy!
    // Removes are blocked by all and any operation types, so can just choose
    // at will.
    api::StorageMessage::SP msg(
            new api::SetBucketStateCommand(makeDocumentBucket(bucket), api::SetBucketStateCommand::ACTIVE));
    msg->setAddress(api::StorageMessageAddress("storage", lib::NodeType::STORAGE, 1));
    _pendingTracker->insert(msg);

    sendReply(op);
    // Should not be a remove here!
    CPPUNIT_ASSERT_EQUAL(merge, _sender.getLastCommand(true));
    CPPUNIT_ASSERT(!op.ok());
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
void
MergeOperationTest::allow_deleting_active_source_only_replica()
{
    getClock().setAbsoluteTimeInSeconds(10);

    addNodesToBucketDB(document::BucketId(16, 1),
                       "0=10/1/1/t,"
                       "1=20/1/1/u/a,"
                       "2=10/1/1/t");

    enableDistributorClusterState("distributor:1 storage:3");
    MergeOperation op(BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(0, 1, 2)));
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    std::string merge(
            "MergeBucketCommand(BucketId(0x4000000000000001), to time "
            "10000000, cluster state version: 0, nodes: [0, 2, 1 "
            "(source only)], chain: [], reasons to start: ) => 0");
    CPPUNIT_ASSERT_EQUAL(merge, _sender.getLastCommand(true));

    sendReply(op);
    CPPUNIT_ASSERT_EQUAL(
            std::string("DeleteBucketCommand(BucketId(0x4000000000000001)) "
                        "Reasons to start:  => 1"),
            _sender.getLastCommand(true));
}

void
MergeOperationTest::testMarkRedundantTrustedCopiesAsSourceOnly()
{
    // This test uses the same distribution as testGenerateNodeList(), i.e.
    // an ideal state sequence of [3, 5, 7, 6, 8, 0, 9, 2, 1, 4]

    // 3 redundancy, 5 trusted -> 2 trusted source only.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6s,8s"),
            getNodeList("storage:10", 3, "3t,5t,7t,6t,8t"));

    // 3 redundancy, 4 trusted -> 1 trusted source only.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6s,8s"),
            getNodeList("storage:10", 3, "3t,5t,7t,6t,8"));

    // 3 redundancy, 3 trusted -> 0 trusted source only, 2 non-trusted sources.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6s,8s"),
            getNodeList("storage:10", 3, "3t,5t,7t,6,8"));

    // Trusted-ness should not be taken into account when marking nodes as source-only.
    // 2 out of 3 ideal replicas trusted.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6s,8s"),
            getNodeList("storage:10", 3, "3t,5t,7,6t,8t"));

    // 1 out of 3 ideal replicas trusted.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6s,8s"),
            getNodeList("storage:10", 3, "3t,5,7,6t,8t"));

    // 0 out of 3 ideal replicas trusted.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6s,8s"),
            getNodeList("storage:10", 3, "3,5,7,6t,8t"));

    // #redundancy of trusted, but none are ideal. Non-ideal trusted may be
    // marked as source only.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6s,8s,0s,9s"),
            getNodeList("storage:10", 3, "3,5,7,6,8t,0t,9t"));

    // Allow for removing excess trusted, non-ideal copies.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6s,8s,0s,9s"),
            getNodeList("storage:10", 3, "3,5,7,6t,8t,0t,9t"));
}

void
MergeOperationTest::onlyMarkRedundantRetiredReplicasAsSourceOnly()
{
    // No nodes in ideal state and all nodes are retired. With redundancy of 2
    // we can only mark the last replica in the DB as source-only. Retired
    // nodes are meant as source-only due to being migrated away from, but
    // source-only nodes will have their replica removed after a successful
    // merge, which we cannot allow to happen here.
    CPPUNIT_ASSERT_EQUAL(
            std::string("1,0,2s"),
            getNodeList("storage:3 .0.s:r .1.s:r .2.s:r", 2, "1,0,2"));
}

void MergeOperationTest::mark_post_merge_redundant_replicas_source_only() {
    // Ideal state sequence is [3, 5, 7, 6, 8, 0, 9, 2, 1, 4]

    // Retired node 7 is not part of the #redundancy ideal state and should be moved
    // to node 6. Once the merge is done we'll end up with too many replicas unless
    // we allow marking the to-be-moved replica as source only.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,6,7s"),
            getNodeList("storage:10 .7.s:r", 3, "3t,5t,7t,6"));

    // Should be allowed to mark as source only even if retired replica is the
    // only trusted replica at the time the merge starts.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,6,7s"),
            getNodeList("storage:10 .7.s:r", 3, "3,5,7t,6"));

    // This extends to multiple retired nodes.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,6,8,5s,7s"),
            getNodeList("storage:10 .5.s:r .7.s:r", 3, "3t,5t,7t,6,8"));

    // If number of post-merge ideal nodes is lower than desired redundancy, don't
    // mark any as source only.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6"),
            getNodeList("storage:10", 5, "3,5,7,6"));

    // Same applies to when post-merge ideal nodes is _equal_ to desired redundancy.
    CPPUNIT_ASSERT_EQUAL(
            std::string("3,5,7,6"),
            getNodeList("storage:10", 4, "3,5,7,6"));
}

void MergeOperationTest::merge_operation_is_blocked_by_any_busy_target_node() {
    getClock().setAbsoluteTimeInSeconds(10);
    addNodesToBucketDB(document::BucketId(16, 1), "0=10/1/1/t,1=20/1/1,2=10/1/1/t");
    enableDistributorClusterState("distributor:1 storage:3");
    MergeOperation op(BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)), toVector<uint16_t>(0, 1, 2)));
    op.setIdealStateManager(&getIdealStateManager());

    // Should not block on nodes _not_ included in operation node set
    _pendingTracker->getNodeInfo().setBusy(3, std::chrono::seconds(10));
    CPPUNIT_ASSERT(!op.isBlocked(*_pendingTracker));

    // Node 1 is included in operation node set and should cause a block
    _pendingTracker->getNodeInfo().setBusy(0, std::chrono::seconds(10));
    CPPUNIT_ASSERT(op.isBlocked(*_pendingTracker));

    getClock().addSecondsToTime(11);
    CPPUNIT_ASSERT(!op.isBlocked(*_pendingTracker)); // No longer busy

    // Should block on other operation nodes than the first listed as well
    _pendingTracker->getNodeInfo().setBusy(1, std::chrono::seconds(10));
    CPPUNIT_ASSERT(op.isBlocked(*_pendingTracker));
}

void MergeOperationTest::missing_replica_is_included_in_limited_node_list() {
    setupDistributor(Redundancy(4), NodeCount(4), "distributor:1 storage:4");
    getClock().setAbsoluteTimeInSeconds(10);
    addNodesToBucketDB(document::BucketId(16, 1), "4=0/0/0/t,1=0/0/0t,2=0/0/0/t");
    const uint16_t max_merge_size = 2;
    MergeOperation op(BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)), toVector<uint16_t>(0, 1, 2, 4)), max_merge_size);
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    // Must include missing node 0 and not just 2 existing replicas
    CPPUNIT_ASSERT_EQUAL(
            std::string("MergeBucketCommand(BucketId(0x4000000000000001), to time 10000000, "
                    "cluster state version: 0, nodes: [0, 4], chain: [], "
                    "reasons to start: ) => 0"),
            _sender.getLastCommand(true));
}

} // distributor
} // storage

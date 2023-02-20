// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/storage/common/reindexing_constants.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/operations/external/visitoroperation.h>
#include <vespa/storage/distributor/operations/external/visitororder.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace document;
using namespace storage::api;
using namespace storage::lib;
using namespace ::testing;
using document::test::makeBucketSpace;

namespace storage::distributor {

struct VisitorOperationTest : Test, DistributorStripeTestUtil {
    VisitorOperationTest()
        : defaultConfig(100, 100)
    {}

    void SetUp() override {
        createLinks();
        nullId = document::BucketId(0, 0);
    };

    void TearDown() override {
        close();
    }

    enum {MAX_PENDING = 2};

    document::BucketId nullId;
    VisitorOperation::Config defaultConfig;

    static api::CreateVisitorCommand::SP
    createVisitorCommand(std::string instanceId,
                         document::BucketId superBucket,
                         document::BucketId lastBucket,
                         uint32_t maxBuckets = 8,
                         vespalib::duration timeout = 500ms,
                         bool visitInconsistentBuckets = false,
                         bool visitRemoves = false,
                         std::string libraryName = "dumpvisitor",
                         const std::string& docSelection = "")
    {
        auto cmd = std::make_shared<api::CreateVisitorCommand>(
                makeBucketSpace(), libraryName, instanceId, docSelection);
        cmd->setControlDestination("controldestination");
        cmd->setDataDestination("datadestination");
        cmd->setFieldSet(document::AllFields::NAME);
        if (visitRemoves) {
            cmd->setVisitRemoves();
        }
        cmd->setFromTime(10);
        cmd->setToTime(100);

        cmd->addBucketToBeVisited(superBucket);
        cmd->addBucketToBeVisited(lastBucket);

        cmd->setMaximumPendingReplyCount(VisitorOperationTest::MAX_PENDING);
        cmd->setMaxBucketsPerVisitor(maxBuckets);
        cmd->setTimeout(timeout);
        if (visitInconsistentBuckets) {
            cmd->setVisitInconsistentBuckets();
        }
        return cmd;
    }

    std::string
    serializeVisitorCommand(int idx = -1) {
        if (idx == -1) {
            idx = _sender.commands().size() - 1;
        }

        std::ostringstream ost;

        auto* cvc = dynamic_cast<CreateVisitorCommand*>(_sender.command(idx).get());
        assert(cvc != nullptr);

        ost << *cvc << " Buckets: [ ";
        for (uint32_t i = 0; i < cvc->getBuckets().size(); ++i) {
            ost << cvc->getBuckets()[i] << " ";
        }
        ost << "]";
        return ost.str();
    }

    VisitorMetricSet& defaultVisitorMetrics() {
        return metrics().visits;
    }

    std::unique_ptr<VisitorOperation> createOpWithConfig(
            api::CreateVisitorCommand::SP msg,
            const VisitorOperation::Config& config)
    {
        return std::make_unique<VisitorOperation>(
                node_context(),
                operation_context(),
                getDistributorBucketSpace(),
                msg,
                config,
                metrics().visits);
    }

    std::unique_ptr<VisitorOperation> createOpWithDefaultConfig(api::CreateVisitorCommand::SP msg)
    {
        return createOpWithConfig(std::move(msg), defaultConfig);
    }

    /**
     * Starts a visitor where we expect no createVisitorCommands to be sent
     * to storage, either due to error or due to no data actually stored.
     */
    std::string runEmptyVisitor(api::CreateVisitorCommand::SP msg) {
        auto op = createOpWithDefaultConfig(std::move(msg));
        op->start(_sender);
        return _sender.getLastReply();
    }

    const std::vector<BucketId>& getBucketsFromLastCommand() {
        const auto& cvc = dynamic_cast<const CreateVisitorCommand&>(*_sender.commands().back());
        return cvc.getBuckets();
    }

    std::pair<std::string, std::string>
    runVisitor(document::BucketId id, document::BucketId lastId, uint32_t maxBuckets);


    void doStandardVisitTest(const std::string& clusterState);

    std::unique_ptr<VisitorOperation> startOperationWith2StorageNodeVisitors(bool inconsistent);

    void do_visitor_roundtrip_with_statistics(const api::ReturnCode& result);
};

TEST_F(VisitorOperationTest, parameter_forwarding) {
    doStandardVisitTest("distributor:1 storage:1");
}

void
VisitorOperationTest::doStandardVisitTest(const std::string& clusterState)
{
    enable_cluster_state(clusterState);

    // Create bucket in bucketdb
    document::BucketId id(uint64_t(0x400000000000007b));
    addNodesToBucketDB(id, "0=1/1/1/t");

    // Send create visitor
    vespalib::string instanceId("testParameterForwarding");
    vespalib::string libraryName("dumpvisitor");
    vespalib::string docSelection("");
    auto msg = std::make_shared<api::CreateVisitorCommand>(
            makeBucketSpace(), libraryName, instanceId, docSelection);
    vespalib::string controlDestination("controldestination");
    msg->setControlDestination(controlDestination);
    vespalib::string dataDestination("datadestination");
    msg->setDataDestination(dataDestination);
    msg->setMaximumPendingReplyCount(VisitorOperationTest::MAX_PENDING);
    msg->setMaxBucketsPerVisitor(8);
    msg->setFromTime(10);
    msg->setToTime(0);
    msg->addBucketToBeVisited(id);
    msg->addBucketToBeVisited(nullId);
    msg->setFieldSet(document::AllFields::NAME);
    msg->setVisitRemoves();
    msg->setTimeout(1234ms);
    msg->getTrace().setLevel(7);

    auto op = createOpWithDefaultConfig(std::move(msg));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));

    // Receive create visitor command for storage and simulate reply
    api::StorageMessage::SP rep0 = _sender.command(0);
    auto* cvc = dynamic_cast<CreateVisitorCommand*>(rep0.get());
    ASSERT_TRUE(cvc != nullptr);
    EXPECT_EQ(libraryName, cvc->getLibraryName());
    EXPECT_EQ(instanceId, cvc->getInstanceId().substr(0, instanceId.length()));
    EXPECT_EQ(docSelection, cvc->getDocumentSelection());
    EXPECT_EQ(controlDestination, cvc->getControlDestination());
    EXPECT_EQ(dataDestination, cvc->getDataDestination());
    EXPECT_EQ(VisitorOperationTest::MAX_PENDING, cvc->getMaximumPendingReplyCount());
    EXPECT_EQ(8, cvc->getMaxBucketsPerVisitor());
    EXPECT_EQ(1, cvc->getBuckets().size());
    EXPECT_EQ(api::Timestamp(10), cvc->getFromTime());
    EXPECT_GT(cvc->getToTime(), 0);
    EXPECT_EQ(document::AllFields::NAME, cvc->getFieldSet());
    EXPECT_TRUE(cvc->visitRemoves());
    EXPECT_EQ(1234ms, cvc->getTimeout());
    EXPECT_EQ(7, cvc->getTrace().getLevel());

    sendReply(*op);

    ASSERT_EQ("CreateVisitorReply("
              "last=BucketId(0x000000007fffffff)) "
              "ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_EQ(1, defaultVisitorMetrics().ok.getLongValue("count"));
}

TEST_F(VisitorOperationTest, shutdown) {
    enable_cluster_state("distributor:1 storage:1");

    // Create bucket in bucketdb
    document::BucketId id(uint64_t(0x400000000000007b));
    addNodesToBucketDB(id, "0=1/1/1/t");

    // Send create visitor
    vespalib::string instanceId("testShutdown");
    vespalib::string libraryName("dumpvisitor");
    vespalib::string docSelection("");
    auto msg = std::make_shared<api::CreateVisitorCommand>(
            makeBucketSpace(), libraryName, instanceId, docSelection);
    msg->addBucketToBeVisited(id);
    msg->addBucketToBeVisited(nullId);

    auto op = createOpWithDefaultConfig(std::move(msg));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));

    op->onClose(_sender); // This will fail the visitor

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(ABORTED, Process is shutting down)",
              _sender.getLastReply());
}

TEST_F(VisitorOperationTest, no_bucket) {
    enable_cluster_state("distributor:1 storage:1");

    // Send create visitor
    auto msg = std::make_shared<api::CreateVisitorCommand>(
            makeBucketSpace(), "dumpvisitor", "instance", "");

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(ILLEGAL_PARAMETERS, No buckets in "
              "CreateVisitorCommand for visitor 'instance')",
              runEmptyVisitor(msg));
}

TEST_F(VisitorOperationTest, none_fieldset_is_rejected) {
    enable_cluster_state("distributor:1 storage:1");
    auto msg = std::make_shared<api::CreateVisitorCommand>(
            makeBucketSpace(), "dumpvisitor", "instance", "");
    msg->addBucketToBeVisited(document::BucketId(16, 1));
    msg->addBucketToBeVisited(nullId);
    msg->setFieldSet("[none]");

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(ILLEGAL_PARAMETERS, Field set '[none]' is not supported "
              "for external visitor operations. Use '[id]' to return documents with no fields set.)",
              runEmptyVisitor(msg));
}

TEST_F(VisitorOperationTest, only_super_bucket_and_progress_allowed) {
    enable_cluster_state("distributor:1 storage:1");

    // Send create visitor
    api::CreateVisitorCommand::SP msg(new api::CreateVisitorCommand(
            makeBucketSpace(), "dumpvisitor", "instance", ""));
    msg->addBucketToBeVisited(nullId);
    msg->addBucketToBeVisited(nullId);
    msg->addBucketToBeVisited(nullId);

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(ILLEGAL_PARAMETERS, CreateVisitorCommand "
              "does not contain 2 buckets for visitor "
              "'instance')",
              runEmptyVisitor(msg));
}

TEST_F(VisitorOperationTest, retired_storage_node) {
    doStandardVisitTest("distributor:1 storage:1 .0.s:r");
}

TEST_F(VisitorOperationTest, no_resend_after_timeout_passed) {
    document::BucketId id(uint64_t(0x400000000000007b));

    enable_cluster_state("distributor:1 storage:2");
    addNodesToBucketDB(id, "0=1/1/1/t,1=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("lowtimeoutbusy", id, nullId, 8, 20ms));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));

    getClock().addMilliSecondsToTime(22);

    sendReply(*op, -1, api::ReturnCode::BUSY);

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(ABORTED, Timeout of 20 ms is running out)",
              _sender.getLastReply());
}

TEST_F(VisitorOperationTest, distributor_not_ready) {
    enable_cluster_state("distributor:0 storage:0");
    document::BucketId id(uint64_t(0x400000000000007b));
    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(NODE_NOT_READY, No distributors available when "
              "processing visitor 'notready')",
              runEmptyVisitor(createVisitorCommand("notready", id, nullId)));
}

TEST_F(VisitorOperationTest, non_existing_bucket) {
    document::BucketId id(uint64_t(0x400000000000007b));
    enable_cluster_state("distributor:1 storage:1");
    auto res = runEmptyVisitor(
            createVisitorCommand("nonExistingBucket", id, nullId));
    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
              "ReturnCode(NONE)", res);
}

TEST_F(VisitorOperationTest, user_single_bucket) {
    document::BucketId id(uint64_t(0x400000000000007b));
    document::BucketId userid(uint64_t(0x800000000000007b));
    enable_cluster_state("distributor:1 storage:1");

    addNodesToBucketDB(id, "0=1/1/1/t");

    auto op = createOpWithDefaultConfig(
        createVisitorCommand(
            "userSingleBucket",
            userid,
            nullId,
            8,
            500ms,
            false,
            false,
            "dumpvisitor",
            "true"));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true)) << _sender.getLastReply();
    sendReply(*op);
    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
              "ReturnCode(NONE)",
              _sender.getLastReply());
}

std::pair<std::string, std::string>
VisitorOperationTest::runVisitor(document::BucketId id,
                                               document::BucketId lastId,
                                               uint32_t maxBuckets)
{
    auto op = createOpWithDefaultConfig(
            createVisitorCommand("inconsistentSplit",
                id,
                lastId,
                maxBuckets,
                500ms,
                false,
                false,
                "dumpvisitor",
                "true"));

    op->start(_sender);

    sendReply(*op);

    std::pair<std::string, std::string> retVal =
        std::make_pair(serializeVisitorCommand(), _sender.getLastReply());

    _sender.clear();

    return retVal;
}

TEST_F(VisitorOperationTest, user_inconsistently_split_bucket) {
    enable_cluster_state("distributor:1 storage:1");

    // Not containing (19, 0x40001)
    addNodesToBucketDB(document::BucketId(17, 0x0), "0=1/1/1/t");
    addNodesToBucketDB(document::BucketId(18, 0x20001), "0=1/1/1/t");
    addNodesToBucketDB(document::BucketId(19, 0x1), "0=1/1/1/t");

    // Containing (19, 0x40001)
    addNodesToBucketDB(document::BucketId(17, 0x1), "0=1/1/1/t");
    addNodesToBucketDB(document::BucketId(18, 0x1), "0=1/1/1/t");

    // Equal to (19, 0x40001)
    addNodesToBucketDB(document::BucketId(19, 0x40001), "0=1/1/1/t");

    // Contained in (19, 0x40001)
    addNodesToBucketDB(document::BucketId(20, 0x40001), "0=1/1/1/t");
    addNodesToBucketDB(document::BucketId(20, 0xc0001), "0=1/1/1/t");
    addNodesToBucketDB(document::BucketId(21, 0x40001), "0=1/1/1/t");
    addNodesToBucketDB(document::BucketId(21, 0x140001), "0=1/1/1/t");

    document::BucketId id(19, 0x40001);

    {
        std::pair<std::string, std::string> val(
                runVisitor(id, nullId, 100));

        EXPECT_EQ("CreateVisitorCommand(dumpvisitor, true, 7 buckets) "
                  "Buckets: [ BucketId(0x4400000000000001) "
                             "BucketId(0x4800000000000001) "
                             "BucketId(0x4c00000000040001) "
                             "BucketId(0x5000000000040001) "
                             "BucketId(0x5400000000040001) "
                             "BucketId(0x5400000000140001) "
                             "BucketId(0x50000000000c0001) ]",
                  val.first);

        EXPECT_EQ("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
                  "ReturnCode(NONE)",
                  val.second);
    }
}

TEST_F(VisitorOperationTest, bucket_removed_while_visitor_pending) {
    enable_cluster_state("distributor:1 storage:1");

    // Create bucket in bucketdb
    document::BucketId id(uint64_t(0x400000000000007b));

    addNodesToBucketDB(id, "0=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("removefrombucketdb", id, nullId));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));

    removeFromBucketDB(id);

    sendReply(*op, -1, api::ReturnCode::NOT_CONNECTED);

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(BUCKET_NOT_FOUND)",
              _sender.getLastReply());
    EXPECT_EQ(1, defaultVisitorMetrics().failures.inconsistent_bucket.getLongValue("count"));
}

TEST_F(VisitorOperationTest, empty_buckets_visited_when_visiting_removes) {
    enable_cluster_state("distributor:1 storage:1");
    document::BucketId id(uint64_t(0x400000000000007b));
    addNodesToBucketDB(id, "0=0/0/0/1/2/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("emptybucket", id, nullId, 8, 500ms, false, true));

    op->start(_sender);

    // Since visitRemoves is true, the empty bucket will be visited
    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));
}

TEST_F(VisitorOperationTest, resend_to_other_storage_node_on_failure) {
    enable_cluster_state("distributor:1 storage:2");
    document::BucketId id(uint64_t(0x400000000000007b));

    addNodesToBucketDB(id, "0=1/1/1/t,1=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("emptyinconsistent", id, nullId));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));

    sendReply(*op, -1, api::ReturnCode::NOT_CONNECTED);
    ASSERT_EQ("", _sender.getReplies());

    ASSERT_EQ("Visitor Create => 0,Visitor Create => 1",
            _sender.getCommands(true));
}

// Since MessageBus handles timeouts for us implicitly, we make the assumption
// that we can safely wait for all replies to be received before sending a
// client reply and that this won't cause things to hang for indeterminate
// amounts of time.
TEST_F(VisitorOperationTest, timeout_only_after_reply_from_all_storage_nodes) {
    enable_cluster_state("distributor:1 storage:2");

    // Contained in (16, 0x1)
    addNodesToBucketDB(document::BucketId(17, 0x00001), "0=1/1/1/t");
    addNodesToBucketDB(document::BucketId(17, 0x10001), "1=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("timeout2bucketson2nodes",
                document::BucketId(16, 1),
                nullId,
                8));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0,Visitor Create => 1",
              _sender.getCommands(true));

    getClock().addMilliSecondsToTime(501);

    sendReply(*op, 0);
    ASSERT_EQ("", _sender.getReplies()); // No reply yet.

    sendReply(*op, 1, api::ReturnCode::BUSY);

    ASSERT_EQ("CreateVisitorReply(last=BucketId(0x4400000000000001)) "
              "ReturnCode(ABORTED, Timeout of 500 ms is running out)",
              _sender.getLastReply());

    // XXX This is sub-optimal in the case that we time out but all storage
    // visitors return OK, as we'll then be failing an operation that
    // technically went fine. However, this is assumed to happen sufficiently
    // rarely (requires timing to be so that mbus timouts don't happen for
    // neither client -> distributor nor distributor -> storage for the
    // operation to possibly have been considered successful) that we
    // don't bother to add complexity for handling it as a special case.
}

TEST_F(VisitorOperationTest, timeout_does_not_override_critical_error) {
    enable_cluster_state("distributor:1 storage:2");
    addNodesToBucketDB(document::BucketId(17, 0x00001), "0=1/1/1/t");
    addNodesToBucketDB(document::BucketId(17, 0x10001), "1=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("timeout2bucketson2nodes",
                document::BucketId(16, 1),
                nullId,
                8,
                500ms)); // ms timeout

    op->start(_sender);
    ASSERT_EQ("Visitor Create => 0,Visitor Create => 1",
              _sender.getCommands(true));

    getClock().addMilliSecondsToTime(501);
    // Technically has timed out at this point, but should still report the
    // critical failure.
    sendReply(*op, 0, api::ReturnCode::INTERNAL_FAILURE);
    ASSERT_EQ("", _sender.getReplies());
    sendReply(*op, 1, api::ReturnCode::BUSY);

    ASSERT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(INTERNAL_FAILURE, [from content node 0] )",
              _sender.getLastReply());
    EXPECT_EQ(1, defaultVisitorMetrics().failures.storagefailure.getLongValue("count"));
}

TEST_F(VisitorOperationTest, wrong_distribution) {
    setup_stripe(1, 100, "distributor:100 storage:2");

    document::BucketId id(uint64_t(0x400000000000127b));
    ASSERT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(WRONG_DISTRIBUTION, distributor:100 storage:2)",
              runEmptyVisitor(createVisitorCommand("wrongdist", id, nullId)));
    EXPECT_EQ(1, defaultVisitorMetrics().failures.wrongdistributor.getLongValue("count"));
}

TEST_F(VisitorOperationTest, wrong_distribution_in_pending_state) {
    // Force bucket to belong to this distributor in currently enabled state.
    setup_stripe(1, 100, "distributor:1 storage:2");
    // Trigger pending cluster state. Note: increase in storage node count
    // to force resending of bucket info requests.
    simulate_set_pending_cluster_state("distributor:100 storage:3");

    document::BucketId id(uint64_t(0x400000000000127b));
    ASSERT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(WRONG_DISTRIBUTION, distributor:100 storage:3)",
              runEmptyVisitor(createVisitorCommand("wrongdistpending", id, nullId)));
}

// If the current node state changes, this alters the node's cluster state
// internally without this change being part of a new version. As a result,
// we cannot answer with WRONG_DISTRIBUTION as the client expects to see a
// higher version number.
// See ticket 6353382 for details.
TEST_F(VisitorOperationTest, visitor_aborted_if_node_is_marked_as_down) {
    setup_stripe(1, 10, "distributor:10 .0.s:s storage:10");

    document::BucketId id(uint64_t(0x400000000000127b));
    ASSERT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(ABORTED, Distributor is shutting down)",
              runEmptyVisitor(createVisitorCommand("wrongdist", id, nullId)));
}

TEST_F(VisitorOperationTest, bucket_high_bit_count) {
    enable_cluster_state("distributor:1 storage:1 bits:16");

    document::BucketId id(18, 0x0);
    addNodesToBucketDB(id, "0=1/1/1/t");

    ASSERT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(WRONG_DISTRIBUTION, distributor:1 storage:1)",
              runEmptyVisitor(createVisitorCommand("buckethigbit", id, nullId)));

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("buckethighbitcount",
                id,
                nullId,
                8,
                500ms,
                false,
                false,
                "dumpvisitor",
                "true"));

    op->start(_sender);

    EXPECT_EQ("Visitor Create => 0", _sender.getCommands(true));
}

TEST_F(VisitorOperationTest, bucket_low_bit_count) {
    enable_cluster_state("distributor:1 storage:1 bits:16");

    document::BucketId id(1, 0x0);
    addNodesToBucketDB(id, "0=1/1/1/t");

    ASSERT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(WRONG_DISTRIBUTION, distributor:1 storage:1)",
              runEmptyVisitor(createVisitorCommand("bucketlowbit", id, nullId)));

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("buckethighbitcount",
                id,
                nullId,
                8,
                500ms,
                false,
                false,
                "dumpvisitor",
                "true"));

    op->start(_sender);
    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(WRONG_DISTRIBUTION, distributor:1 storage:1)",
              _sender.getLastReply());
}

TEST_F(VisitorOperationTest, parallel_visitors_to_one_storage_node) {
    enable_cluster_state("distributor:1 storage:1");

    // Create buckets in bucketdb
    for (int i=0; i<32; i++) {
        document::BucketId id(21, i*0x10000 + 0x0001);
        addNodesToBucketDB(id, "0=1/1/1/t");
    }

    document::BucketId id(16, 1);

    auto op = createOpWithConfig(
            createVisitorCommand("multiplebuckets", id, nullId, 31),
            VisitorOperation::Config(1, 4));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0,Visitor Create => 0,"
              "Visitor Create => 0,Visitor Create => 0",
              _sender.getCommands(true));

    ASSERT_EQ("CreateVisitorCommand(dumpvisitor, , 8 buckets) Buckets: [ "
              "BucketId(0x5400000000000001) BucketId(0x5400000000040001) "
              "BucketId(0x5400000000020001) BucketId(0x5400000000060001) "
              "BucketId(0x5400000000010001) BucketId(0x5400000000050001) "
              "BucketId(0x5400000000030001) BucketId(0x5400000000070001) ]",
              serializeVisitorCommand(0));
    ASSERT_EQ("CreateVisitorCommand(dumpvisitor, , 8 buckets) Buckets: [ "
              "BucketId(0x5400000000100001) BucketId(0x5400000000140001) "
              "BucketId(0x5400000000120001) BucketId(0x5400000000160001) "
              "BucketId(0x5400000000110001) BucketId(0x5400000000150001) "
              "BucketId(0x5400000000130001) BucketId(0x5400000000170001) ]",
              serializeVisitorCommand(1));
    ASSERT_EQ("CreateVisitorCommand(dumpvisitor, , 8 buckets) Buckets: [ "
              "BucketId(0x5400000000080001) BucketId(0x54000000000c0001) "
              "BucketId(0x54000000000a0001) BucketId(0x54000000000e0001) "
              "BucketId(0x5400000000090001) BucketId(0x54000000000d0001) "
              "BucketId(0x54000000000b0001) BucketId(0x54000000000f0001) ]",
              serializeVisitorCommand(2));
    ASSERT_EQ("CreateVisitorCommand(dumpvisitor, , 7 buckets) Buckets: [ "
              "BucketId(0x5400000000180001) BucketId(0x54000000001c0001) "
              "BucketId(0x54000000001a0001) BucketId(0x54000000001e0001) "
              "BucketId(0x5400000000190001) BucketId(0x54000000001d0001) "
              "BucketId(0x54000000001b0001) ]",
              serializeVisitorCommand(3));

    for (uint32_t i = 0; i < 4; ++i) {
        sendReply(*op, i);
    }

    ASSERT_EQ("CreateVisitorReply(last=BucketId(0x54000000000f0001)) "
              "ReturnCode(NONE)",
              _sender.getLastReply());

    _sender.clear();

    uint32_t minBucketsPerVisitor = 1;
    uint32_t maxVisitorsPerNode = 4;
    auto op2 = createOpWithConfig(
            createVisitorCommand("multiplebuckets", id, document::BucketId(0x54000000000f0001), 31),
            VisitorOperation::Config(minBucketsPerVisitor, maxVisitorsPerNode));

    op2->start(_sender);

    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));

    sendReply(*op2);

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
              "ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(VisitorOperationTest, parallel_visitors_resend_only_failing) {
    enable_cluster_state("distributor:1 storage:2");

    // Create buckets in bucketdb
    for (int i=0; i<32; i++) {
        document::BucketId id(21, i*0x10000 + 0x0001);
        addNodesToBucketDB(id, "0=1/1/1/t,1=1/1/1/t");
    }

    document::BucketId id(16, 1);

    uint32_t minBucketsPerVisitor = 5;
    uint32_t maxVisitorsPerNode = 4;
    auto op = createOpWithConfig(
            createVisitorCommand("multiplebuckets", id, nullId, 31),
            VisitorOperation::Config(minBucketsPerVisitor, maxVisitorsPerNode));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0,Visitor Create => 0,"
              "Visitor Create => 0,Visitor Create => 0",
              _sender.getCommands(true));

    for (uint32_t i = 0; i < 2; ++i) {
        sendReply(*op, i, api::ReturnCode::NOT_CONNECTED);
    }

    ASSERT_EQ("Visitor Create => 0,Visitor Create => 0,"
              "Visitor Create => 0,Visitor Create => 0,"
              "Visitor Create => 1,Visitor Create => 1",
              _sender.getCommands(true));

    for (uint32_t i = 2; i < 6; ++i) {
        sendReply(*op, i);
    }

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x54000000000f0001)) "
              "ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(VisitorOperationTest, parallel_visitors_to_one_storage_node_one_super_bucket) {
    enable_cluster_state("distributor:1 storage:1");

    // Create buckets in bucketdb
    for (int i=0; i<8; i++) {
        document::BucketId id(0x8c000000e3362b6aULL+i*0x100000000ull);
        addNodesToBucketDB(id, "0=1/1/1/t");
    }

    document::BucketId id(16, 0x2b6a);

    auto op = createOpWithConfig(
            createVisitorCommand("multiplebucketsonesuper", id, nullId),
            VisitorOperation::Config(5, 4));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));

    ASSERT_EQ("CreateVisitorCommand(dumpvisitor, , 8 buckets) Buckets: [ "
              "BucketId(0x8c000000e3362b6a) BucketId(0x8c000004e3362b6a) "
              "BucketId(0x8c000002e3362b6a) BucketId(0x8c000006e3362b6a) "
              "BucketId(0x8c000001e3362b6a) BucketId(0x8c000005e3362b6a) "
              "BucketId(0x8c000003e3362b6a) BucketId(0x8c000007e3362b6a) ]",
              serializeVisitorCommand(0));

    sendReply(*op);
    
    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
              "ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(VisitorOperationTest, visit_when_one_bucket_copy_is_invalid) {
    enable_cluster_state("distributor:1 storage:2");

    document::BucketId id(16, 0);

    addNodesToBucketDB(id, "0=100,1=0/0/1");

    ASSERT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(BUCKET_NOT_FOUND)",
              runEmptyVisitor(createVisitorCommand("incompletehandling", id, nullId)));
}

TEST_F(VisitorOperationTest, visiting_when_all_buckets_are_invalid) {
    enable_cluster_state("distributor:1 storage:2");

    document::BucketId id(16, 0);

    addNodesToBucketDB(id, "0=0/0/1,1=0/0/1");

    ASSERT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(BUCKET_NOT_FOUND)",
              runEmptyVisitor(createVisitorCommand("allincompletehandling", id, nullId)));
}

TEST_F(VisitorOperationTest, inconsistency_handling) {
    enable_cluster_state("distributor:1 storage:2");

    document::BucketId id(16, 0);

    addNodesToBucketDB(id, "0=1/1/1,1=2/2/2");

    ASSERT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(BUCKET_NOT_FOUND)",
              runEmptyVisitor(createVisitorCommand("testinconsistencyhandling", id, nullId)));
    _sender.clear();

    auto op = createOpWithConfig(
            createVisitorCommand("multiplebucketsonesuper", id, nullId, 8, 500ms, true),
            VisitorOperation::Config(5, 4));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 1", _sender.getCommands(true));

    sendReply(*op);

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
              "ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(VisitorOperationTest, visit_ideal_node) {
    ClusterState state("distributor:1 storage:3");
    enable_cluster_state(lib::ClusterStateBundle(state));

    // Create buckets in bucketdb
    for (int i=0; i<32; i++ ) {
        document::BucketId id(21, i*0x10000 + 0x0001);
        addIdealNodes(state, id);
    }

    document::BucketId id(16, 1);
    auto op = createOpWithDefaultConfig(
            createVisitorCommand("multinode", id, nullId, 8));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));

    ASSERT_EQ("CreateVisitorCommand(dumpvisitor, , 8 buckets) Buckets: [ "
              "BucketId(0x5400000000000001) BucketId(0x5400000000100001) "
              "BucketId(0x5400000000080001) BucketId(0x5400000000180001) "
              "BucketId(0x5400000000040001) BucketId(0x5400000000140001) "
              "BucketId(0x54000000000c0001) BucketId(0x54000000001c0001) ]",
              serializeVisitorCommand(0));

    sendReply(*op);

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x54000000001c0001)) "
              "ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(VisitorOperationTest, no_resending_on_critical_failure) {
    enable_cluster_state("distributor:1 storage:3");

    // Create buckets in bucketdb
    for (int i=0; i<32; i++ ) {
        document::BucketId id(21, i*0x10000 + 0x0001);
        addNodesToBucketDB(id, "0=1/1/1/t,1=1/1/1/t");
    }

    document::BucketId id(16, 1);
    auto op = createOpWithDefaultConfig(
            createVisitorCommand("multinodefailurecritical", id, nullId, 8));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));

    sendReply(*op, -1, api::ReturnCode::ILLEGAL_PARAMETERS);

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(ILLEGAL_PARAMETERS, [from content node 0] )",
              _sender.getLastReply());
}

TEST_F(VisitorOperationTest, failure_on_all_nodes) {
    enable_cluster_state("distributor:1 storage:3");

    // Create buckets in bucketdb
    for (int i=0; i<32; i++ ) {
        document::BucketId id(21, i*0x10000 + 0x0001);
        addNodesToBucketDB(id, "0=1/1/1/t,1=1/1/1/t");
    }

    document::BucketId id(16, 1);
    auto op = createOpWithDefaultConfig(
            createVisitorCommand("multinodefailurecritical", id, nullId, 8));

    op->start(_sender);

    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));

    sendReply(*op, -1, api::ReturnCode::NOT_CONNECTED);

    ASSERT_EQ("Visitor Create => 0,Visitor Create => 1", _sender.getCommands(true));

    sendReply(*op, -1, api::ReturnCode::NOT_CONNECTED);

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(BUCKET_NOT_FOUND)",
              _sender.getLastReply());
    // TODO it'd be much more accurate to increase the "notconnected" metric
    // here, but our metrics are currently based on the reply sent back to the
    // client, not the ones sent from the content nodes to the distributor.
}

TEST_F(VisitorOperationTest, visit_in_chunks) {
    enable_cluster_state("distributor:1 storage:1");

    for (int i = 0; i < 9; ++i) {
        addNodesToBucketDB(document::BucketId(30, i << 16), "0=1/1/1/t");
    }

    document::BucketId id(16, 0);

    std::pair<std::string, std::string> val(runVisitor(id, nullId, 3));
    EXPECT_EQ("CreateVisitorCommand(dumpvisitor, true, 3 buckets) "
              "Buckets: [ BucketId(0x7800000000000000) "
              "BucketId(0x7800000000080000) "
              "BucketId(0x7800000000040000) ]",
              val.first);

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x7800000000040000)) "
              "ReturnCode(NONE)",
              val.second);

    val = runVisitor(id, document::BucketId(0x7800000000040000), 3);
    EXPECT_EQ("CreateVisitorCommand(dumpvisitor, true, 3 buckets) "
              "Buckets: [ BucketId(0x7800000000020000) "
              "BucketId(0x7800000000060000) "
              "BucketId(0x7800000000010000) ]",
              val.first);

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x7800000000010000)) "
              "ReturnCode(NONE)",
              val.second);

    val = runVisitor(id, document::BucketId(0x7800000000010000), 3);
    EXPECT_EQ("CreateVisitorCommand(dumpvisitor, true, 3 buckets) "
              "Buckets: [ BucketId(0x7800000000050000) "
              "BucketId(0x7800000000030000) "
              "BucketId(0x7800000000070000) ]",
              val.first);

    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
              "ReturnCode(NONE)",
              val.second);
}

std::unique_ptr<VisitorOperation>
VisitorOperationTest::startOperationWith2StorageNodeVisitors(bool inconsistent)
{
    enable_cluster_state("distributor:1 storage:3");

    addNodesToBucketDB(document::BucketId(17, 1), "0=1/1/1/t");
    addNodesToBucketDB(document::BucketId(17, 1ULL << 16 | 1), "1=1/1/1/t");

    document::BucketId id(16, 1);
    auto op = createOpWithDefaultConfig(
            createVisitorCommand(
                "multinodefailurecritical",
                id,
                nullId,
                8,
                500ms,
                inconsistent));

    op->start(_sender);

    assert(_sender.getCommands(true) == "Visitor Create => 0,Visitor Create => 1");
    return op;
}

TEST_F(VisitorOperationTest, no_client_reply_before_all_storage_replies_received) {
    auto op = startOperationWith2StorageNodeVisitors(false);

    sendReply(*op, 0, api::ReturnCode::BUSY);
    // We don't want to see a reply here until the other node has replied.
    ASSERT_EQ("", _sender.getReplies(true));
    // OK reply from 1, but have to retry from client anyhow since one of
    // the sub buckets failed to be processed and we don't have inconsistent
    // visiting set in the client visitor command.
    sendReply(*op, 1);
    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(BUCKET_NOT_FOUND)",
              _sender.getLastReply());
    // XXX we should consider wether we want BUSY to be returned instead.
    // Non-critical error codes are currently converted to a generic "not found"
    // code to let the client silently retry until the bucket has hopefully
    // become consistent/available.
}

TEST_F(VisitorOperationTest, skip_failed_sub_buckets_when_visiting_inconsistent) {
    auto op = startOperationWith2StorageNodeVisitors(true);

    sendReply(*op, 0, api::ReturnCode::BUSY);
    ASSERT_EQ("", _sender.getReplies(true));
    // Subset of buckets could not be visited, but visit inconsistent flag is
    // set in the client visitor so we treat it as a success anyway. In this
    // case we've expanded the entire superbucket sub-tree so return with magic
    // number to signify this.
    sendReply(*op, 1);
    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
              "ReturnCode(NONE)",
              _sender.getLastReply());
}

// By default, queue timeout should be half of remaining visitor time. This
// is a highly un-scientific heuristic, but seems rather more reasonable than
// having it hard-coded to 2000 ms as was the case earlier.
TEST_F(VisitorOperationTest, queue_timeout_is_factor_of_total_timeout) {
    document::BucketId id(uint64_t(0x400000000000007b));
    enable_cluster_state("distributor:1 storage:2");
    addNodesToBucketDB(id, "0=1/1/1/t,1=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("foo", id, nullId, 8, 10000ms));

    op->start(_sender);
    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));

    auto& cmd = dynamic_cast<CreateVisitorCommand&>(*_sender.command(0));
    EXPECT_EQ(5000ms, cmd.getQueueTimeout());
}

void
VisitorOperationTest::do_visitor_roundtrip_with_statistics(
        const api::ReturnCode& result)
{
    document::BucketId id(0x400000000000007bULL);
    enable_cluster_state("distributor:1 storage:1");
    addNodesToBucketDB(id, "0=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("metricstats", id, nullId));

    op->start(_sender);
    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));
    auto& cmd = dynamic_cast<CreateVisitorCommand&>(*_sender.command(0));
    auto reply = cmd.makeReply();
    vdslib::VisitorStatistics stats;
    stats.setBucketsVisited(50);
    stats.setDocumentsVisited(100);
    stats.setBytesVisited(2000);
    dynamic_cast<CreateVisitorReply&>(*reply).setVisitorStatistics(stats);
    reply->setResult(result);

    op->receive(_sender, api::StorageReply::SP(std::move(reply)));
}

TEST_F(VisitorOperationTest, metrics_are_updated_with_visitor_statistics_upon_replying) {
    ASSERT_NO_FATAL_FAILURE(do_visitor_roundtrip_with_statistics(api::ReturnCode(api::ReturnCode::OK)));

    EXPECT_EQ(50, defaultVisitorMetrics().buckets_per_visitor.getLast());
    EXPECT_EQ(100, defaultVisitorMetrics().docs_per_visitor.getLast());
    EXPECT_EQ(2000, defaultVisitorMetrics().bytes_per_visitor.getLast());
}

TEST_F(VisitorOperationTest, statistical_metrics_not_updated_on_wrong_distribution) {
    setup_stripe(1, 100, "distributor:100 storage:2");

    document::BucketId id(uint64_t(0x400000000000127b));
    ASSERT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(WRONG_DISTRIBUTION, distributor:100 storage:2)",
              runEmptyVisitor(createVisitorCommand("wrongdist", id, nullId)));

    // Note that we're testing the number of _times_ the metric has been
    // updated, not the value with which it's been updated (which would be zero
    // even in the case we actually did update the statistical metrics).
    EXPECT_EQ(0, defaultVisitorMetrics().buckets_per_visitor.getCount());
    EXPECT_EQ(0, defaultVisitorMetrics().docs_per_visitor.getCount());
    EXPECT_EQ(0, defaultVisitorMetrics().bytes_per_visitor.getCount());
    // Fascinating that count is also a double...
    EXPECT_DOUBLE_EQ(0.0, defaultVisitorMetrics().latency.getCount());
}

TEST_F(VisitorOperationTest, assigning_put_lock_access_token_sets_special_visitor_parameter) {
    document::BucketId id(0x400000000000007bULL);
    enable_cluster_state("distributor:1 storage:1");
    addNodesToBucketDB(id, "0=1/1/1/t");

    auto op = createOpWithDefaultConfig(createVisitorCommand("metricstats", id, nullId));
    op->assign_put_lock_access_token("its-a me, mario");

    op->start(_sender);
    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));
    auto cmd = std::dynamic_pointer_cast<api::CreateVisitorCommand>(_sender.command(0));
    ASSERT_TRUE(cmd);
    EXPECT_EQ(cmd->getParameters().get(reindexing_bucket_lock_visitor_parameter_key(),
                                       vespalib::stringref("")),
              "its-a me, mario");
}

}

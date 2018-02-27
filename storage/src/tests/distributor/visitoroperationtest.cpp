// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <iomanip>
#include <iostream>
#include <memory>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storage/distributor/operations/external/visitoroperation.h>
#include <vespa/storage/distributor/operations/external/visitororder.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/storage/distributor/distributor.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/document/test/make_bucket_space.h>

using namespace document;
using namespace storage::api;
using namespace storage::lib;
using namespace std::string_literals;
using document::test::makeBucketSpace;

namespace storage::distributor {

class VisitorOperationTest : public CppUnit::TestFixture,
                             public DistributorTestUtil {
    CPPUNIT_TEST_SUITE(VisitorOperationTest);
    CPPUNIT_TEST(testParameterForwarding);
    CPPUNIT_TEST(testShutdown);
    CPPUNIT_TEST(testNoBucket);
    CPPUNIT_TEST(testOnlySuperBucketAndProgressAllowed);
    CPPUNIT_TEST(testRetiredStorageNode);
    CPPUNIT_TEST(testNoResendAfterTimeoutPassed);
    CPPUNIT_TEST(testDistributorNotReady);
    CPPUNIT_TEST(testInvalidOrderDocSelection);
    CPPUNIT_TEST(testNonExistingBucket);
    CPPUNIT_TEST(testUserSingleBucket);
    CPPUNIT_TEST(testUserInconsistentlySplitBucket);
    CPPUNIT_TEST(testBucketRemovedWhileVisitorPending);
    CPPUNIT_TEST(testEmptyBucketsVisitedWhenVisitingRemoves);
    CPPUNIT_TEST(testResendToOtherStorageNodeOnFailure);
    CPPUNIT_TEST(testTimeoutOnlyAfterReplyFromAllStorageNodes);
    CPPUNIT_TEST(testTimeoutDoesNotOverrideCriticalError);
    CPPUNIT_TEST(testWrongDistribution);
    CPPUNIT_TEST(testWrongDistributionInPendingState);
    CPPUNIT_TEST(testVisitorAbortedIfNodeIsMarkedAsDown);
    CPPUNIT_TEST(testBucketHighBitCount);
    CPPUNIT_TEST(testBucketLowBitCount);
    CPPUNIT_TEST(testParallelVisitorsToOneStorageNode);
    CPPUNIT_TEST(testParallelVisitorsResendOnlyFailing);
    CPPUNIT_TEST(testParallelVisitorsToOneStorageNodeOneSuperBucket);
    CPPUNIT_TEST(testVisitWhenOneBucketCopyIsInvalid);
    CPPUNIT_TEST(testVisitingWhenAllBucketsAreInvalid);
    CPPUNIT_TEST(testInconsistencyHandling);
    CPPUNIT_TEST(testVisitIdealNode);
    CPPUNIT_TEST(testNoResendingOnCriticalFailure);
    CPPUNIT_TEST(testFailureOnAllNodes);
    CPPUNIT_TEST(testVisitOrder);
    CPPUNIT_TEST(testVisitInChunks);
    CPPUNIT_TEST(testVisitOrderSplitPastOrderBits);
    CPPUNIT_TEST(testVisitOrderInconsistentlySplit);
    CPPUNIT_TEST(testUserVisitorOrder);
    CPPUNIT_TEST(testUserVisitorOrderSplitPastOrderBits);
    CPPUNIT_TEST(testNoClientReplyBeforeAllStorageRepliesReceived);
    CPPUNIT_TEST(testSkipFailedSubBucketsWhenVisitingInconsistent);
    CPPUNIT_TEST(testQueueTimeoutIsFactorOfTotalTimeout);
    CPPUNIT_TEST(metrics_are_updated_with_visitor_statistics_upon_replying);
    CPPUNIT_TEST(statistical_metrics_not_updated_on_wrong_distribution);
    CPPUNIT_TEST_SUITE_END();

protected:
    void testParameterForwarding();
    void testShutdown();
    void testNoBucket();
    void testOnlySuperBucketAndProgressAllowed();
    void testRetiredStorageNode();
    void testNoResendAfterTimeoutPassed();
    void testDistributorNotReady();
    void testInvalidOrderDocSelection();
    void testNonExistingBucket();
    void testUserSingleBucket();
    void testUserInconsistentlySplitBucket();
    void testBucketRemovedWhileVisitorPending();
    void testEmptyBucketsVisitedWhenVisitingRemoves();
    void testResendToOtherStorageNodeOnFailure();
    void testTimeoutOnlyAfterReplyFromAllStorageNodes();
    void testTimeoutDoesNotOverrideCriticalError();
    void testAbortNonExisting();
    void testAbort();
    void testWrongDistribution();
    void testWrongDistributionInPendingState();
    void testVisitorAbortedIfNodeIsMarkedAsDown();
    void testBucketHighBitCount();
    void testBucketLowBitCount();
    void testParallelVisitorsToOneStorageNode();
    void testParallelVisitorsResendOnlyFailing();
    void testParallelVisitorsToOneStorageNodeOneSuperBucket();
    void testVisitWhenOneBucketCopyIsInvalid();
    void testVisitingWhenAllBucketsAreInvalid();
    void testInconsistencyHandling();
    void testVisitIdealNode();
    void testNoResendingOnCriticalFailure();
    void testFailureOnAllNodes();
    void testVisitOrder();
    void testVisitInChunks();
    void testVisitOrderSplitPastOrderBits();
    void testVisitOrderInconsistentlySplit();
    void testUserVisitorOrder();
    void testUserVisitorOrderSplitPastOrderBits();
    void testUserVisitorOrderInconsistentlySplit();
    void testNoClientReplyBeforeAllStorageRepliesReceived();
    void testSkipFailedSubBucketsWhenVisitingInconsistent();
    void testQueueTimeoutIsFactorOfTotalTimeout();
    void metrics_are_updated_with_visitor_statistics_upon_replying();
    void statistical_metrics_not_updated_on_wrong_distribution();
public:
    VisitorOperationTest()
        : defaultConfig(100, 100)
    {}

    void setUp() override {
        createLinks();
        nullId = document::BucketId(0, 0);
        doneId = document::BucketId(INT_MAX);
    };

    void tearDown() override {
        close();
    }

    enum {MAX_PENDING = 2};
private:
    document::BucketId nullId;
    document::BucketId doneId;
    VisitorOperation::Config defaultConfig;

    api::CreateVisitorCommand::SP
    createVisitorCommand(std::string instanceId,
                         document::BucketId superBucket,
                         document::BucketId lastBucket,
                         uint32_t maxBuckets = 8,
                         uint32_t timeoutMS = 500,
                         bool visitInconsistentBuckets = false,
                         bool visitRemoves = false,
                         std::string libraryName = "dumpvisitor",
                         document::OrderingSpecification::Order visitorOrdering =
                         document::OrderingSpecification::ASCENDING,
                         const std::string& docSelection = "")
    {
        api::CreateVisitorCommand::SP cmd(
                new api::CreateVisitorCommand(makeBucketSpace(), libraryName, instanceId, docSelection));
        cmd->setControlDestination("controldestination");
        cmd->setDataDestination("datadestination");
        cmd->setFieldSet("[header]");
        if (visitRemoves) {
            cmd->setVisitRemoves();
        }
        cmd->setFromTime(10);
        cmd->setToTime(100);

        cmd->addBucketToBeVisited(superBucket);
        cmd->addBucketToBeVisited(lastBucket);

        cmd->setMaximumPendingReplyCount(VisitorOperationTest::MAX_PENDING);
        cmd->setMaxBucketsPerVisitor(maxBuckets);
        cmd->setTimeout(timeoutMS);
        if (visitInconsistentBuckets) {
            cmd->setVisitInconsistentBuckets();
        }
        cmd->setVisitorOrdering(visitorOrdering);
        return cmd;
    }

    std::string
    serializeVisitorCommand(int idx = -1) {
        if (idx == -1) {
            idx = _sender.commands.size() - 1;
        }

        std::ostringstream ost;

        CreateVisitorCommand* cvc = dynamic_cast<CreateVisitorCommand*>(
                _sender.commands[idx].get());

        ost << *cvc << " Buckets: [ ";
        for (uint32_t i = 0; i < cvc->getBuckets().size(); ++i) {
            ost << cvc->getBuckets()[i] << " ";
        }
        ost << "]";
        return ost.str();
    }

    VisitorMetricSet& defaultVisitorMetrics() {
        return getDistributor().getMetrics().visits[documentapi::LoadType::DEFAULT];
    }

    std::unique_ptr<VisitorOperation> createOpWithConfig(
            api::CreateVisitorCommand::SP msg,
            const VisitorOperation::Config& config)
    {
        return std::make_unique<VisitorOperation>(
                getExternalOperationHandler(),
                getDistributorBucketSpace(),
                msg,
                config,
                getDistributor().getMetrics().visits[msg->getLoadType()]);
    }

    std::unique_ptr<VisitorOperation> createOpWithDefaultConfig(
            api::CreateVisitorCommand::SP msg)
    {
        return createOpWithConfig(std::move(msg), defaultConfig);
    }

    /**
     * Starts a visitor where we expect no createVisitorCommands to be sent
     * to storage, either due to error or due to no data actually stored.
     */
    std::string runEmptyVisitor(api::CreateVisitorCommand::SP msg) {
        auto op = createOpWithDefaultConfig(std::move(msg));
        op->start(_sender, framework::MilliSecTime(0));
        return _sender.getLastReply();
    }

    const std::vector<BucketId>& getBucketsFromLastCommand() {
        const CreateVisitorCommand& cvc(
                dynamic_cast<const CreateVisitorCommand&>(
                    *_sender.commands[_sender.commands.size() - 1]));
        return cvc.getBuckets();
    }

    std::pair<std::string, std::string>
    runVisitor(document::BucketId id,
                           document::BucketId lastId,
                           uint32_t maxBuckets);

    std::string doOrderedVisitor(document::BucketId startBucket);

    void doStandardVisitTest(const std::string& clusterState);

    std::unique_ptr<VisitorOperation> startOperationWith2StorageNodeVisitors(
            bool inconsistent);

    void do_visitor_roundtrip_with_statistics(const api::ReturnCode& result);
};

CPPUNIT_TEST_SUITE_REGISTRATION(VisitorOperationTest);

void
VisitorOperationTest::testParameterForwarding()
{
    doStandardVisitTest("distributor:1 storage:1");
}

void
VisitorOperationTest::doStandardVisitTest(const std::string& clusterState)
{
    enableDistributorClusterState(clusterState);

    // Create bucket in bucketdb
    document::BucketId id(uint64_t(0x400000000000007b));
    addNodesToBucketDB(id, "0=1/1/1/t");

    // Send create visitor
    vespalib::string instanceId("testParameterForwarding");
    vespalib::string libraryName("dumpvisitor");
    vespalib::string docSelection("");
    api::CreateVisitorCommand::SP msg(
            new api::CreateVisitorCommand(makeBucketSpace(),
                                          libraryName,
                                          instanceId,
                                          docSelection));
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
    msg->setFieldSet("[header]");
    msg->setVisitRemoves();
    msg->setTimeout(1234);
    msg->getTrace().setLevel(7);

    auto op = createOpWithDefaultConfig(std::move(msg));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));

    // Receive create visitor command for storage and simulate reply
    api::StorageMessage::SP rep0 = _sender.commands[0];
    CreateVisitorCommand* cvc = dynamic_cast<CreateVisitorCommand*>(rep0.get());
    CPPUNIT_ASSERT(cvc);
    CPPUNIT_ASSERT_EQUAL(libraryName, cvc->getLibraryName());
    CPPUNIT_ASSERT_EQUAL(instanceId, cvc->getInstanceId().substr(0, instanceId.length()));
    CPPUNIT_ASSERT_EQUAL(docSelection, cvc->getDocumentSelection());
    CPPUNIT_ASSERT_EQUAL(controlDestination, cvc->getControlDestination());
    CPPUNIT_ASSERT_EQUAL(dataDestination, cvc->getDataDestination());
    CPPUNIT_ASSERT_EQUAL((unsigned int) VisitorOperationTest::MAX_PENDING, cvc->getMaximumPendingReplyCount());
    CPPUNIT_ASSERT_EQUAL((unsigned int) 8, cvc->getMaxBucketsPerVisitor());
    CPPUNIT_ASSERT_EQUAL((size_t) 1, cvc->getBuckets().size());
    CPPUNIT_ASSERT_EQUAL((api::Timestamp) 10, cvc->getFromTime());
    CPPUNIT_ASSERT(cvc->getToTime() > 0);
    CPPUNIT_ASSERT_EQUAL(vespalib::string("[header]"), cvc->getFieldSet());
    CPPUNIT_ASSERT_EQUAL((bool) 1, cvc->visitRemoves());
    CPPUNIT_ASSERT_EQUAL(uint32_t(1234), cvc->getTimeout());
    CPPUNIT_ASSERT_EQUAL(uint32_t(7), cvc->getTrace().getLevel());

    sendReply(*op);

    CPPUNIT_ASSERT_EQUAL(std::string("CreateVisitorReply("
                                     "last=BucketId(0x000000007fffffff)) "
                                     "ReturnCode(NONE)"),
                         _sender.getLastReply());
    CPPUNIT_ASSERT_EQUAL(int64_t(1), defaultVisitorMetrics().
                                     ok.getLongValue("count"));
}

void
VisitorOperationTest::testShutdown()
{
    enableDistributorClusterState("distributor:1 storage:1");

    // Create bucket in bucketdb
    document::BucketId id(uint64_t(0x400000000000007b));
    addNodesToBucketDB(id, "0=1/1/1/t");

    // Send create visitor
    vespalib::string instanceId("testShutdown");
    vespalib::string libraryName("dumpvisitor");
    vespalib::string docSelection("");
    api::CreateVisitorCommand::SP msg(
            new api::CreateVisitorCommand(makeBucketSpace(),
                                          libraryName,
                                          instanceId,
                                          docSelection));
    msg->addBucketToBeVisited(id);
    msg->addBucketToBeVisited(nullId);

    auto op = createOpWithDefaultConfig(std::move(msg));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));

    op->onClose(_sender); // This will fail the visitor

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(ABORTED, Process is shutting down)"),
            _sender.getLastReply());
}

void
VisitorOperationTest::testNoBucket()
{
    enableDistributorClusterState("distributor:1 storage:1");

    // Send create visitor
    api::CreateVisitorCommand::SP msg(new api::CreateVisitorCommand(
            makeBucketSpace(), "dumpvisitor", "instance", ""));

    CPPUNIT_ASSERT_EQUAL(std::string(
                     "CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                     "ReturnCode(ILLEGAL_PARAMETERS, No buckets in "
                     "CreateVisitorCommand for visitor 'instance')"),
                 runEmptyVisitor(msg));
}

void
VisitorOperationTest::testOnlySuperBucketAndProgressAllowed()
{
    enableDistributorClusterState("distributor:1 storage:1");

    // Send create visitor
    api::CreateVisitorCommand::SP msg(new api::CreateVisitorCommand(
            makeBucketSpace(), "dumpvisitor", "instance", ""));
    msg->addBucketToBeVisited(nullId);
    msg->addBucketToBeVisited(nullId);
    msg->addBucketToBeVisited(nullId);

    CPPUNIT_ASSERT_EQUAL(std::string(
                 "CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                 "ReturnCode(ILLEGAL_PARAMETERS, CreateVisitorCommand "
                 "does not contain 2 buckets for visitor "
                 "'instance')"),
            runEmptyVisitor(msg));
}

void
VisitorOperationTest::testRetiredStorageNode()
{
    doStandardVisitTest("distributor:1 storage:1 .0.s:r");
}

void
VisitorOperationTest::testNoResendAfterTimeoutPassed()
{
    document::BucketId id(uint64_t(0x400000000000007b));

    enableDistributorClusterState("distributor:1 storage:2");
    addNodesToBucketDB(id, "0=1/1/1/t,1=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("lowtimeoutbusy", id, nullId, 8, 20));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));

    getClock().addMilliSecondsToTime(22);

    sendReply(*op, -1, api::ReturnCode::BUSY);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                    "ReturnCode(ABORTED, Timeout of 20 ms is running out)"),
            _sender.getLastReply());
}

void
VisitorOperationTest::testDistributorNotReady()
{
    enableDistributorClusterState("distributor:0 storage:0");
    document::BucketId id(uint64_t(0x400000000000007b));
    CPPUNIT_ASSERT_EQUAL(
            std::string(
              "CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(NODE_NOT_READY, No distributors available when "
              "processing visitor 'notready')"),
            runEmptyVisitor(createVisitorCommand("notready", id, nullId)));
}

// Distributor only parses selection if in the order doc case (which is detected
// by first checking if string contains "order" which it must to refer to
// "id.order" !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
void
VisitorOperationTest::testInvalidOrderDocSelection()
{
    enableDistributorClusterState("distributor:1 storage:1");
    document::BucketId id(0x400000000000007b);
    addNodesToBucketDB(id, "0=1/1/1/t");

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(ILLEGAL_PARAMETERS, Failed to parse document select "
                        "string 'id.order(10,3)=1 and dummy': Document type 'dummy' not "
                        "found at column 22 when parsing selection 'id.order(10,3)=1 and dummy')"),
            runEmptyVisitor(
                    createVisitorCommand("invalidOrderDoc",
                            id,
                            nullId,
                            8,
                            500,
                            false,
                            false,
                            "dumpvisitor",
                            document::OrderingSpecification::ASCENDING,
                            "id.order(10,3)=1 and dummy")));
}

void
VisitorOperationTest::testNonExistingBucket()
{
    document::BucketId id(uint64_t(0x400000000000007b));
    enableDistributorClusterState("distributor:1 storage:1");
    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
                        "ReturnCode(NONE)"),
            runEmptyVisitor(
                    createVisitorCommand("nonExistingBucket",
                            id,
                            nullId)));
}

void
VisitorOperationTest::testUserSingleBucket()
{
    document::BucketId id(uint64_t(0x400000000000007b));
    document::BucketId userid(uint64_t(0x800000000000007b));
    enableDistributorClusterState("distributor:1 storage:1");

    addNodesToBucketDB(id, "0=1/1/1/t");

    auto op = createOpWithDefaultConfig(
        createVisitorCommand(
            "userSingleBucket",
            userid,
            nullId,
            8,
            500,
            false,
            false,
            "dumpvisitor",
            document::OrderingSpecification::ASCENDING,
            "true"));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL_MSG(_sender.getLastReply(),
                             std::string("Visitor Create => 0"),
                             _sender.getCommands(true));
    sendReply(*op);
    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
                        "ReturnCode(NONE)"),
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
                500,
                false,
                false,
                "dumpvisitor",
                document::OrderingSpecification::ASCENDING,
                "true"));

    op->start(_sender, framework::MilliSecTime(0));

    sendReply(*op);

    std::pair<std::string, std::string> retVal =
        std::make_pair(serializeVisitorCommand(), _sender.getLastReply());

    _sender.clear();

    return retVal;
}

void
VisitorOperationTest::testUserInconsistentlySplitBucket()
{
    enableDistributorClusterState("distributor:1 storage:1");

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

        CPPUNIT_ASSERT_EQUAL(std::string(
                "CreateVisitorCommand(dumpvisitor, true, 7 buckets) "
                "Buckets: [ BucketId(0x4400000000000001) "
                           "BucketId(0x4800000000000001) "
                           "BucketId(0x4c00000000040001) "
                           "BucketId(0x5000000000040001) "
                           "BucketId(0x5400000000040001) "
                           "BucketId(0x5400000000140001) "
                           "BucketId(0x50000000000c0001) ]"),
                             val.first);

        CPPUNIT_ASSERT_EQUAL(std::string(
                "CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
                "ReturnCode(NONE)"),
                             val.second);
    }
}

void
VisitorOperationTest::testBucketRemovedWhileVisitorPending()
{
    enableDistributorClusterState("distributor:1 storage:1");

    // Create bucket in bucketdb
    document::BucketId id(uint64_t(0x400000000000007b));

    addNodesToBucketDB(id, "0=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("removefrombucketdb", id, nullId));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));

    removeFromBucketDB(id);

    sendReply(*op, -1, api::ReturnCode::NOT_CONNECTED);

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(BUCKET_NOT_FOUND)"),
            _sender.getLastReply());
    CPPUNIT_ASSERT_EQUAL(int64_t(1), defaultVisitorMetrics().failures.
                                     inconsistent_bucket.getLongValue("count"));
}

void
VisitorOperationTest::testEmptyBucketsVisitedWhenVisitingRemoves()
{
    enableDistributorClusterState("distributor:1 storage:1");
    document::BucketId id(uint64_t(0x400000000000007b));
    addNodesToBucketDB(id, "0=0/0/0/1/2/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("emptybucket",
                id,
                nullId,
                8,
                500,
                false,
                true));

    op->start(_sender, framework::MilliSecTime(0));

    // Since visitRemoves is true, the empty bucket will be visited
    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));
}

void
VisitorOperationTest::testResendToOtherStorageNodeOnFailure()
{
    enableDistributorClusterState("distributor:1 storage:2");
    document::BucketId id(uint64_t(0x400000000000007b));

    addNodesToBucketDB(id, "0=1/1/1/t,1=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("emptyinconsistent", id, nullId));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));

    sendReply(*op, -1, api::ReturnCode::NOT_CONNECTED);
    CPPUNIT_ASSERT_EQUAL(""s, _sender.getReplies());

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0,Visitor Create => 1"),
                         _sender.getCommands(true));
}

// Since MessageBus handles timeouts for us implicitly, we make the assumption
// that we can safely wait for all replies to be received before sending a
// client reply and that this won't cause things to hang for indeterminate
// amounts of time.
void
VisitorOperationTest::testTimeoutOnlyAfterReplyFromAllStorageNodes()
{
    enableDistributorClusterState("distributor:1 storage:2");

    // Contained in (16, 0x1)
    addNodesToBucketDB(document::BucketId(17, 0x00001), "0=1/1/1/t");
    addNodesToBucketDB(document::BucketId(17, 0x10001), "1=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("timeout2bucketson2nodes",
                document::BucketId(16, 1),
                nullId,
                8));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL("Visitor Create => 0,Visitor Create => 1"s,
                         _sender.getCommands(true));

    getClock().addMilliSecondsToTime(501);

    sendReply(*op, 0);
    CPPUNIT_ASSERT_EQUAL(""s, _sender.getReplies()); // No reply yet.

    sendReply(*op, 1, api::ReturnCode::BUSY);

    CPPUNIT_ASSERT_EQUAL(
            "CreateVisitorReply(last=BucketId(0x4400000000000001)) "
            "ReturnCode(ABORTED, Timeout of 500 ms is running out)"s,
            _sender.getLastReply());

    // XXX This is sub-optimal in the case that we time out but all storage
    // visitors return OK, as we'll then be failing an operation that
    // technically went fine. However, this is assumed to happen sufficiently
    // rarely (requires timing to be so that mbus timouts don't happen for
    // neither client -> distributor nor distributor -> storage for the
    // operation to possibly could have been considered successful) that we
    // don't bother to add complexity for handling it as a special case.
}

void
VisitorOperationTest::testTimeoutDoesNotOverrideCriticalError()
{
    enableDistributorClusterState("distributor:1 storage:2");
    addNodesToBucketDB(document::BucketId(17, 0x00001), "0=1/1/1/t");
    addNodesToBucketDB(document::BucketId(17, 0x10001), "1=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("timeout2bucketson2nodes",
                document::BucketId(16, 1),
                nullId,
                8,
                500)); // ms timeout

    op->start(_sender, framework::MilliSecTime(0));
    CPPUNIT_ASSERT_EQUAL("Visitor Create => 0,Visitor Create => 1"s,
                         _sender.getCommands(true));

    getClock().addMilliSecondsToTime(501);
    // Technically has timed out at this point, but should still report the
    // critical failure.
    sendReply(*op, 0, api::ReturnCode::INTERNAL_FAILURE);
    CPPUNIT_ASSERT_EQUAL(""s, _sender.getReplies());
    sendReply(*op, 1, api::ReturnCode::BUSY);

    CPPUNIT_ASSERT_EQUAL(
            "CreateVisitorReply(last=BucketId(0x0000000000000000)) "
            "ReturnCode(INTERNAL_FAILURE, [from content node 0] )"s,
            _sender.getLastReply());
    CPPUNIT_ASSERT_EQUAL(int64_t(1), defaultVisitorMetrics().failures.
                                     storagefailure.getLongValue("count"));
}

void
VisitorOperationTest::testWrongDistribution()
{
    setupDistributor(1, 100, "distributor:100 storage:2");

    document::BucketId id(uint64_t(0x400000000000127b));
    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(WRONG_DISTRIBUTION, distributor:100 storage:2)"),
            runEmptyVisitor(createVisitorCommand("wrongdist", id, nullId)));
    CPPUNIT_ASSERT_EQUAL(int64_t(1), defaultVisitorMetrics().failures.
                                     wrongdistributor.getLongValue("count"));
}

void
VisitorOperationTest::testWrongDistributionInPendingState()
{
    // Force bucket to belong to this distributor in currently enabled state.
    setupDistributor(1, 100, "distributor:1 storage:2");
    // Trigger pending cluster state. Note: increase in storage node count
    // to force resending of bucket info requests.
    auto stateCmd = std::make_shared<api::SetSystemStateCommand>(
            lib::ClusterState("distributor:100 storage:3"));
    getBucketDBUpdater().onSetSystemState(stateCmd);

    document::BucketId id(uint64_t(0x400000000000127b));
    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(WRONG_DISTRIBUTION, distributor:100 storage:3)"),
            runEmptyVisitor(createVisitorCommand("wrongdistpending", id, nullId)));
}

// If the current node state changes, this alters the node's cluster state
// internally without this change being part of a new version. As a result,
// we cannot answer with WRONG_DISTRIBUTION as the client expects to see a
// higher version number.
// See ticket 6353382 for details.
void
VisitorOperationTest::testVisitorAbortedIfNodeIsMarkedAsDown()
{
    setupDistributor(1, 10, "distributor:10 .0.s:s storage:10");

    document::BucketId id(uint64_t(0x400000000000127b));
    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(ABORTED, Distributor is shutting down)"),
            runEmptyVisitor(createVisitorCommand("wrongdist", id, nullId)));
}

void
VisitorOperationTest::testBucketHighBitCount()
{
    enableDistributorClusterState("distributor:1 storage:1 bits:16");

    document::BucketId id(18, 0x0);
    addNodesToBucketDB(id, "0=1/1/1/t");

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(WRONG_DISTRIBUTION, distributor:1 storage:1)"),
            runEmptyVisitor(createVisitorCommand("buckethigbit", id, nullId)));

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("buckethighbitcount",
                id,
                nullId,
                8,
                500,
                false,
                false,
                "dumpvisitor",
                document::OrderingSpecification::ASCENDING,
                "true"));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));
}

void
VisitorOperationTest::testBucketLowBitCount()
{
    enableDistributorClusterState("distributor:1 storage:1 bits:16");

    document::BucketId id(1, 0x0);
    addNodesToBucketDB(id, "0=1/1/1/t");

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(WRONG_DISTRIBUTION, distributor:1 storage:1)"),
            runEmptyVisitor(createVisitorCommand("bucketlowbit", id, nullId)));

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("buckethighbitcount",
                id,
                nullId,
                8,
                500,
                false,
                false,
                "dumpvisitor",
                document::OrderingSpecification::ASCENDING,
                "true"));

    op->start(_sender, framework::MilliSecTime(0));
    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(WRONG_DISTRIBUTION, distributor:1 storage:1)"),
            _sender.getLastReply());
}

void
VisitorOperationTest::testParallelVisitorsToOneStorageNode()
{
    enableDistributorClusterState("distributor:1 storage:1");

    // Create buckets in bucketdb
    for (int i=0; i<32; i++) {
        document::BucketId id(21, i*0x10000 + 0x0001);
        addNodesToBucketDB(id, "0=1/1/1/t");
    }

    document::BucketId id(16, 1);

    auto op = createOpWithConfig(
            createVisitorCommand("multiplebuckets", id, nullId, 31),
            VisitorOperation::Config(1, 4));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0,Visitor Create => 0,"
                                     "Visitor Create => 0,Visitor Create => 0"),
                         _sender.getCommands(true));

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorCommand(dumpvisitor, , 8 buckets) Buckets: [ "
                        "BucketId(0x5400000000000001) BucketId(0x5400000000040001) "
                        "BucketId(0x5400000000020001) BucketId(0x5400000000060001) "
                        "BucketId(0x5400000000010001) BucketId(0x5400000000050001) "
                        "BucketId(0x5400000000030001) BucketId(0x5400000000070001) ]"),
            serializeVisitorCommand(0));
    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorCommand(dumpvisitor, , 8 buckets) Buckets: [ "
                        "BucketId(0x5400000000100001) BucketId(0x5400000000140001) "
                        "BucketId(0x5400000000120001) BucketId(0x5400000000160001) "
                        "BucketId(0x5400000000110001) BucketId(0x5400000000150001) "
                        "BucketId(0x5400000000130001) BucketId(0x5400000000170001) ]"),
            serializeVisitorCommand(1));
    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorCommand(dumpvisitor, , 8 buckets) Buckets: [ "
                        "BucketId(0x5400000000080001) BucketId(0x54000000000c0001) "
                        "BucketId(0x54000000000a0001) BucketId(0x54000000000e0001) "
                        "BucketId(0x5400000000090001) BucketId(0x54000000000d0001) "
                        "BucketId(0x54000000000b0001) BucketId(0x54000000000f0001) ]"),
                         serializeVisitorCommand(2));
    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorCommand(dumpvisitor, , 7 buckets) Buckets: [ "
                        "BucketId(0x5400000000180001) BucketId(0x54000000001c0001) "
                        "BucketId(0x54000000001a0001) BucketId(0x54000000001e0001) "
                        "BucketId(0x5400000000190001) BucketId(0x54000000001d0001) "
                        "BucketId(0x54000000001b0001) ]"),
            serializeVisitorCommand(3));

    for (uint32_t i = 0; i < 4; ++i) {
        sendReply(*op, i);
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x54000000000f0001)) "
                        "ReturnCode(NONE)"),
            _sender.getLastReply());

    _sender.clear();

    uint32_t minBucketsPerVisitor = 1;
    uint32_t maxVisitorsPerNode = 4;
    auto op2 = createOpWithConfig(
            createVisitorCommand("multiplebuckets", id, document::BucketId(0x54000000000f0001), 31),
            VisitorOperation::Config(minBucketsPerVisitor, maxVisitorsPerNode));

    op2->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));

    sendReply(*op2);

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
                        "ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
VisitorOperationTest::testParallelVisitorsResendOnlyFailing()
{
    enableDistributorClusterState("distributor:1 storage:2");

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

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0,Visitor Create => 0,"
                                     "Visitor Create => 0,Visitor Create => 0"),
                         _sender.getCommands(true));

    for (uint32_t i = 0; i < 2; ++i) {
        sendReply(*op, i, api::ReturnCode::NOT_CONNECTED);
    }

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0,Visitor Create => 0,"
                                     "Visitor Create => 0,Visitor Create => 0,"
                                     "Visitor Create => 1,Visitor Create => 1"),
                         _sender.getCommands(true));

    for (uint32_t i = 2; i < 6; ++i) {
        sendReply(*op, i);
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x54000000000f0001)) "
                        "ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
VisitorOperationTest::testParallelVisitorsToOneStorageNodeOneSuperBucket()
{
    enableDistributorClusterState("distributor:1 storage:1");

    // Create buckets in bucketdb
    for (int i=0; i<8; i++) {
        document::BucketId id(0x8c000000e3362b6aULL+i*0x100000000ull);
        addNodesToBucketDB(id, "0=1/1/1/t");
    }

    document::BucketId id(16, 0x2b6a);

    auto op = createOpWithConfig(
            createVisitorCommand("multiplebucketsonesuper", id, nullId),
            VisitorOperation::Config(5, 4));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorCommand(dumpvisitor, , 8 buckets) Buckets: [ "
                        "BucketId(0x8c000000e3362b6a) BucketId(0x8c000004e3362b6a) "
                        "BucketId(0x8c000002e3362b6a) BucketId(0x8c000006e3362b6a) "
                        "BucketId(0x8c000001e3362b6a) BucketId(0x8c000005e3362b6a) "
                        "BucketId(0x8c000003e3362b6a) BucketId(0x8c000007e3362b6a) ]"),
            serializeVisitorCommand(0));

    sendReply(*op);
    
    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
                        "ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
VisitorOperationTest::testVisitWhenOneBucketCopyIsInvalid()
{
    enableDistributorClusterState("distributor:1 storage:2");

    document::BucketId id(16, 0);

    addNodesToBucketDB(id, "0=100,1=0/0/1");

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(BUCKET_NOT_FOUND)"),
            runEmptyVisitor(createVisitorCommand("incompletehandling",
                                               id,
                                               nullId)));
}

void
VisitorOperationTest::testVisitingWhenAllBucketsAreInvalid()
{
    enableDistributorClusterState("distributor:1 storage:2");

    document::BucketId id(16, 0);

    addNodesToBucketDB(id, "0=0/0/1,1=0/0/1");

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(BUCKET_NOT_FOUND)"),
            runEmptyVisitor(createVisitorCommand("allincompletehandling",
                                               id,
                                               nullId)));
}

void
VisitorOperationTest::testInconsistencyHandling()
{
    enableDistributorClusterState("distributor:1 storage:2");

    document::BucketId id(16, 0);

    addNodesToBucketDB(id, "0=1/1/1,1=2/2/2");

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(BUCKET_NOT_FOUND)"),
            runEmptyVisitor(createVisitorCommand("testinconsistencyhandling",
                                               id,
                                               nullId)));
    _sender.clear();

    auto op = createOpWithConfig(
            createVisitorCommand("multiplebucketsonesuper", id, nullId, 8, 500, true),
            VisitorOperation::Config(5, 4));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 1"),
                         _sender.getCommands(true));

    sendReply(*op);

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
                        "ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
VisitorOperationTest::testVisitIdealNode()
{
    ClusterState state("distributor:1 storage:3");
    _distributor->enableClusterStateBundle(lib::ClusterStateBundle(state));

    // Create buckets in bucketdb
    for (int i=0; i<32; i++ ) {
        document::BucketId id(21, i*0x10000 + 0x0001);
        addIdealNodes(state, id);
    }

    document::BucketId id(16, 1);
    auto op = createOpWithDefaultConfig(
            createVisitorCommand("multinode", id, nullId, 8));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorCommand(dumpvisitor, , 8 buckets) Buckets: [ "
                        "BucketId(0x5400000000000001) BucketId(0x5400000000100001) "
                        "BucketId(0x5400000000080001) BucketId(0x5400000000180001) "
                        "BucketId(0x5400000000040001) BucketId(0x5400000000140001) "
                        "BucketId(0x54000000000c0001) BucketId(0x54000000001c0001) ]"),
            serializeVisitorCommand(0));

    sendReply(*op);

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x54000000001c0001)) "
                        "ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
VisitorOperationTest::testNoResendingOnCriticalFailure()
{
    enableDistributorClusterState("distributor:1 storage:3");

    // Create buckets in bucketdb
    for (int i=0; i<32; i++ ) {
        document::BucketId id(21, i*0x10000 + 0x0001);
        addNodesToBucketDB(id, "0=1/1/1/t,1=1/1/1/t");
    }

    document::BucketId id(16, 1);
    auto op = createOpWithDefaultConfig(
            createVisitorCommand("multinodefailurecritical", id, nullId, 8));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));

    sendReply(*op, -1, api::ReturnCode::ILLEGAL_PARAMETERS);

    CPPUNIT_ASSERT_EQUAL(
            "CreateVisitorReply(last=BucketId(0x0000000000000000)) "
            "ReturnCode(ILLEGAL_PARAMETERS, [from content node 0] )"s,
            _sender.getLastReply());
}

void
VisitorOperationTest::testFailureOnAllNodes()
{
    enableDistributorClusterState("distributor:1 storage:3");

    // Create buckets in bucketdb
    for (int i=0; i<32; i++ ) {
        document::BucketId id(21, i*0x10000 + 0x0001);
        addNodesToBucketDB(id, "0=1/1/1/t,1=1/1/1/t");
    }

    document::BucketId id(16, 1);
    auto op = createOpWithDefaultConfig(
            createVisitorCommand("multinodefailurecritical", id, nullId, 8));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));

    sendReply(*op, -1, api::ReturnCode::NOT_CONNECTED);

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0,Visitor Create => 1"),
                         _sender.getCommands(true));

    sendReply(*op, -1, api::ReturnCode::NOT_CONNECTED);

    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(BUCKET_NOT_FOUND)"),
            _sender.getLastReply());
    // TODO it'd be much more accurate to increase the "notconnected" metric
    // here, but our metrics are currently based on the reply sent back to the
    // client, not the ones sent from the content nodes to the distributor.
}


void
VisitorOperationTest::testVisitOrder()
{
    std::vector<document::BucketId> buckets;

    document::BucketId id000(35, 0x0000004d2);
    buckets.push_back(id000);
    document::BucketId id001(35, 0x4000004d2);
    buckets.push_back(id001);
    document::BucketId id01(34, 0x2000004d2);
    buckets.push_back(id01);
    document::BucketId id1(33, 0x1000004d2);
    buckets.push_back(id1);

    std::sort(buckets.begin(),
              buckets.end(),
              VisitorOrder(document::OrderingSpecification(
                  document::OrderingSpecification::ASCENDING, 0x0, 6, 2)));

    CPPUNIT_ASSERT_EQUAL(buckets[0], id000);
    CPPUNIT_ASSERT_EQUAL(buckets[1], id001);
    CPPUNIT_ASSERT_EQUAL(buckets[2], id01);
    CPPUNIT_ASSERT_EQUAL(buckets[3], id1);

    std::sort(buckets.begin(),
              buckets.end(),
              VisitorOrder(document::OrderingSpecification(
                  document::OrderingSpecification::DESCENDING, 0xFF, 6, 2)));
    CPPUNIT_ASSERT_EQUAL(buckets[0], id1);
    CPPUNIT_ASSERT_EQUAL(buckets[1], id01);
    CPPUNIT_ASSERT_EQUAL(buckets[2], id001);
    CPPUNIT_ASSERT_EQUAL(buckets[3], id000);

    std::sort(buckets.begin(),
              buckets.end(),
              VisitorOrder(document::OrderingSpecification(
                  document::OrderingSpecification::ASCENDING, 0x14, 6, 2)));
    CPPUNIT_ASSERT_EQUAL(buckets[0], id01);
    CPPUNIT_ASSERT_EQUAL(buckets[1], id1);
    CPPUNIT_ASSERT_EQUAL(buckets[2], id000);
    CPPUNIT_ASSERT_EQUAL(buckets[3], id001);

    std::sort(buckets.begin(),
              buckets.end(),
              VisitorOrder(document::OrderingSpecification(
                  document::OrderingSpecification::DESCENDING, 0x14, 6, 2)));
    CPPUNIT_ASSERT_EQUAL(buckets[0], id01);
    CPPUNIT_ASSERT_EQUAL(buckets[1], id001);
    CPPUNIT_ASSERT_EQUAL(buckets[2], id000);
    CPPUNIT_ASSERT_EQUAL(buckets[3], id1);
}

void
VisitorOperationTest::testVisitInChunks()
{
    enableDistributorClusterState("distributor:1 storage:1");

    for (int i = 0; i < 9; ++i) {
        addNodesToBucketDB(document::BucketId(30, i << 16), "0=1/1/1/t");
    }

    document::BucketId id(16, 0);

    std::pair<std::string, std::string> val(runVisitor(id, nullId, 3));
    CPPUNIT_ASSERT_EQUAL(std::string(
                         "CreateVisitorCommand(dumpvisitor, true, 3 buckets) "
                         "Buckets: [ BucketId(0x7800000000000000) "
                         "BucketId(0x7800000000080000) "
                         "BucketId(0x7800000000040000) ]"),
                         val.first);

    CPPUNIT_ASSERT_EQUAL(std::string(
                         "CreateVisitorReply(last=BucketId(0x7800000000040000)) "
                         "ReturnCode(NONE)"),
                         val.second);

    val = runVisitor(id, document::BucketId(0x7800000000040000), 3);
    CPPUNIT_ASSERT_EQUAL(std::string(
                         "CreateVisitorCommand(dumpvisitor, true, 3 buckets) "
                         "Buckets: [ BucketId(0x7800000000020000) "
                         "BucketId(0x7800000000060000) "
                         "BucketId(0x7800000000010000) ]"),
                         val.first);

    CPPUNIT_ASSERT_EQUAL(std::string(
                         "CreateVisitorReply(last=BucketId(0x7800000000010000)) "
                         "ReturnCode(NONE)"),
                         val.second);

    val = runVisitor(id, document::BucketId(0x7800000000010000), 3);
    CPPUNIT_ASSERT_EQUAL(std::string(
                         "CreateVisitorCommand(dumpvisitor, true, 3 buckets) "
                         "Buckets: [ BucketId(0x7800000000050000) "
                         "BucketId(0x7800000000030000) "
                         "BucketId(0x7800000000070000) ]"),
                         val.first);

    CPPUNIT_ASSERT_EQUAL(std::string(
                         "CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
                         "ReturnCode(NONE)"),
                         val.second);
}

void
VisitorOperationTest::testVisitOrderSplitPastOrderBits()
{
    std::vector<document::BucketId> buckets;

    document::BucketId max(INT_MAX);
    buckets.push_back(max);
    document::BucketId id1(33, 0x1000004d2);
    buckets.push_back(id1);
    document::BucketId id01(34, 0x2000004d2);
    buckets.push_back(id01);
    document::BucketId id00001(37, 0x10000004d2);
    buckets.push_back(id00001);
    document::BucketId id00000(37, 0x00000004d2);
    buckets.push_back(id00000);
    document::BucketId id0000(36, 0x0000004d2);
    buckets.push_back(id0000);
    document::BucketId null(0, 0);
    buckets.push_back(null);

    std::sort(buckets.begin(), buckets.end(), VisitorOrder(document::OrderingSpecification(document::OrderingSpecification::ASCENDING, 0x0, 6, 2)));
    CPPUNIT_ASSERT_EQUAL(buckets[0], null);
    CPPUNIT_ASSERT_EQUAL(buckets[1], id0000);
    CPPUNIT_ASSERT_EQUAL(buckets[2], id00000);
    CPPUNIT_ASSERT_EQUAL(buckets[3], id00001);
    CPPUNIT_ASSERT_EQUAL(buckets[4], id01);
    CPPUNIT_ASSERT_EQUAL(buckets[5], id1);
    CPPUNIT_ASSERT_EQUAL(buckets[6], max);

    std::sort(buckets.begin(), buckets.end(), VisitorOrder(document::OrderingSpecification(document::OrderingSpecification::DESCENDING, 0xFF, 6, 2)));
    CPPUNIT_ASSERT_EQUAL(buckets[0], null);
    CPPUNIT_ASSERT_EQUAL(buckets[1], id1);
    CPPUNIT_ASSERT_EQUAL(buckets[2], id01);
    CPPUNIT_ASSERT_EQUAL(buckets[3], id0000);
    CPPUNIT_ASSERT_EQUAL(buckets[4], id00000);
    CPPUNIT_ASSERT_EQUAL(buckets[5], id00001);
    CPPUNIT_ASSERT_EQUAL(buckets[6], max);

    std::sort(buckets.begin(), buckets.end(), VisitorOrder(document::OrderingSpecification(document::OrderingSpecification::ASCENDING, 0x14, 6, 2)));
    CPPUNIT_ASSERT_EQUAL(buckets[0], null);
    CPPUNIT_ASSERT_EQUAL(buckets[1], id01);
    CPPUNIT_ASSERT_EQUAL(buckets[2], id1);
    CPPUNIT_ASSERT_EQUAL(buckets[3], id0000);
    CPPUNIT_ASSERT_EQUAL(buckets[4], id00000);
    CPPUNIT_ASSERT_EQUAL(buckets[5], id00001);
    CPPUNIT_ASSERT_EQUAL(buckets[6], max);

    std::sort(buckets.begin(), buckets.end(), VisitorOrder(document::OrderingSpecification(document::OrderingSpecification::DESCENDING, 0x14, 6, 2)));
    CPPUNIT_ASSERT_EQUAL(buckets[0], null);
    CPPUNIT_ASSERT_EQUAL(buckets[1], id01);
    CPPUNIT_ASSERT_EQUAL(buckets[2], id0000);
    CPPUNIT_ASSERT_EQUAL(buckets[3], id00000);
    CPPUNIT_ASSERT_EQUAL(buckets[4], id00001);
    CPPUNIT_ASSERT_EQUAL(buckets[5], id1);
    CPPUNIT_ASSERT_EQUAL(buckets[6], max);
}

void
VisitorOperationTest::testVisitOrderInconsistentlySplit()
{
    std::vector<document::BucketId> buckets;

    document::BucketId max(INT_MAX);
    buckets.push_back(max);
    document::BucketId id000(35, 0x0000004d2);
    buckets.push_back(id000);
    document::BucketId id001(35, 0x4000004d2);
    buckets.push_back(id001);
    document::BucketId id01(34, 0x2000004d2);
    buckets.push_back(id01);
    document::BucketId id1(33, 0x1000004d2);
    buckets.push_back(id1);
    document::BucketId idsuper(16, 0x04d2);
    buckets.push_back(idsuper);
    document::BucketId null(0, 0);
    buckets.push_back(null);

    std::sort(buckets.begin(), buckets.end(), VisitorOrder(document::OrderingSpecification(document::OrderingSpecification::ASCENDING, 0x0, 6, 2)));
    CPPUNIT_ASSERT_EQUAL(buckets[0], null);
    CPPUNIT_ASSERT_EQUAL(buckets[1], idsuper);
    CPPUNIT_ASSERT_EQUAL(buckets[2], id000);
    CPPUNIT_ASSERT_EQUAL(buckets[3], id001);
    CPPUNIT_ASSERT_EQUAL(buckets[4], id01);
    CPPUNIT_ASSERT_EQUAL(buckets[5], id1);
    CPPUNIT_ASSERT_EQUAL(buckets[6], max);

    std::sort(buckets.begin(), buckets.end(), VisitorOrder(document::OrderingSpecification(document::OrderingSpecification::DESCENDING, 0xFF, 6, 2)));
    CPPUNIT_ASSERT_EQUAL(buckets[0], null);
    CPPUNIT_ASSERT_EQUAL(buckets[1], idsuper);
    CPPUNIT_ASSERT_EQUAL(buckets[2], id1);
    CPPUNIT_ASSERT_EQUAL(buckets[3], id01);
    CPPUNIT_ASSERT_EQUAL(buckets[4], id001);
    CPPUNIT_ASSERT_EQUAL(buckets[5], id000);
    CPPUNIT_ASSERT_EQUAL(buckets[6], max);

    std::sort(buckets.begin(), buckets.end(), VisitorOrder(document::OrderingSpecification(document::OrderingSpecification::ASCENDING, 0x14, 6, 2)));
    CPPUNIT_ASSERT_EQUAL(buckets[0], null);
    CPPUNIT_ASSERT_EQUAL(buckets[1], idsuper);
    CPPUNIT_ASSERT_EQUAL(buckets[2], id01);
    CPPUNIT_ASSERT_EQUAL(buckets[3], id1);
    CPPUNIT_ASSERT_EQUAL(buckets[4], id000);
    CPPUNIT_ASSERT_EQUAL(buckets[5], id001);
    CPPUNIT_ASSERT_EQUAL(buckets[6], max);

    std::sort(buckets.begin(), buckets.end(), VisitorOrder(document::OrderingSpecification(document::OrderingSpecification::DESCENDING, 0x14, 6, 2)));
    CPPUNIT_ASSERT_EQUAL(buckets[0], null);
    CPPUNIT_ASSERT_EQUAL(buckets[1], idsuper);
    CPPUNIT_ASSERT_EQUAL(buckets[2], id01);
    CPPUNIT_ASSERT_EQUAL(buckets[3], id001);
    CPPUNIT_ASSERT_EQUAL(buckets[4], id000);
    CPPUNIT_ASSERT_EQUAL(buckets[5], id1);
    CPPUNIT_ASSERT_EQUAL(buckets[6], max);
}

std::string
VisitorOperationTest::doOrderedVisitor(document::BucketId startBucket)
{
    std::vector<document::BucketId> buckets;

    while (true) {
        _sender.clear();

        auto op = createOpWithDefaultConfig(
                createVisitorCommand(
                    "uservisitororder",
                    startBucket,
                    buckets.size() ? buckets[buckets.size() - 1] :
                    nullId,
                    1,
                    500,
                    false,
                    false,
                    "dumpvisitor",
                    document::OrderingSpecification::DESCENDING,
                    "id.order(6,2)<= 20"));

        op->start(_sender, framework::MilliSecTime(0));

        CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                             _sender.getCommands(true));

        for (uint32_t i = 0; i < _sender.commands.size(); ++i) {
            const api::CreateVisitorCommand cmd(
                    static_cast<const api::CreateVisitorCommand&>(
                            *_sender.commands[i]));

            for (uint32_t j = 0; j < cmd.getBuckets().size(); ++j) {
                buckets.push_back(cmd.getBuckets()[j]);
            }
        }

        sendReply(*op);

        CPPUNIT_ASSERT_EQUAL(1, (int)_sender.replies.size());

        const api::CreateVisitorReply& reply(
                static_cast<const api::CreateVisitorReply&>(*_sender.replies[0]));

        if (reply.getLastBucket() == document::BucketId(0x000000007fffffff)) {
            break;
        }
    }

    std::ostringstream ost;
    for (uint32_t i = 0; i < buckets.size(); ++i) {
        ost << buckets[i] << "\n";
    }

    return ost.str();
}

void
VisitorOperationTest::testUserVisitorOrder()
{
    enableDistributorClusterState("distributor:1 storage:1");

    // Create buckets in bucketdb
    std::vector<document::BucketId> buckets;
    document::BucketId id000(35, 0x0000004d2);
    buckets.push_back(id000);
    document::BucketId id001(35, 0x4000004d2);
    buckets.push_back(id001);
    document::BucketId id01(34, 0x2000004d2);
    buckets.push_back(id01);
    document::BucketId id1(33, 0x1000004d2);
    buckets.push_back(id1);

    for (uint32_t i=0; i<buckets.size(); i++) {
        addNodesToBucketDB(buckets[i], "0=1/1/1/t");
    }

    document::BucketId id(16, 0x04d2);

    CPPUNIT_ASSERT_EQUAL(std::string("BucketId(0x88000002000004d2)\n"
                                     "BucketId(0x8c000004000004d2)\n"
                                     "BucketId(0x8c000000000004d2)\n"
                                     "BucketId(0x84000001000004d2)\n"),
                         doOrderedVisitor(id));
}

void
VisitorOperationTest::testUserVisitorOrderSplitPastOrderBits()
{
    enableDistributorClusterState("distributor:1 storage:1");

    // Create buckets in bucketdb
    std::vector<document::BucketId> buckets;
    document::BucketId id1(33, 0x1000004d2);
    buckets.push_back(id1);
    document::BucketId id01(34, 0x2000004d2);
    buckets.push_back(id01);
    document::BucketId id00001(37, 0x10000004d2);
    buckets.push_back(id00001);
    document::BucketId id00000(37, 0x00000004d2);
    buckets.push_back(id00000);
    document::BucketId id0000(36, 0x0000004d2);
    buckets.push_back(id0000);
    for (uint32_t i=0; i<buckets.size(); i++) {
        addNodesToBucketDB(buckets[i], "0=1/1/1/t");
    }

    document::BucketId id(16, 0x04d2);

    CPPUNIT_ASSERT_EQUAL(std::string("BucketId(0x88000002000004d2)\n"
                                     "BucketId(0x90000000000004d2)\n"
                                     "BucketId(0x94000000000004d2)\n"
                                     "BucketId(0x94000010000004d2)\n"
                                     "BucketId(0x84000001000004d2)\n"),
                         doOrderedVisitor(id));
}

std::unique_ptr<VisitorOperation>
VisitorOperationTest::startOperationWith2StorageNodeVisitors(bool inconsistent)
{
    enableDistributorClusterState("distributor:1 storage:3");

    addNodesToBucketDB(document::BucketId(17, 1), "0=1/1/1/t");
    addNodesToBucketDB(document::BucketId(17, 1 << 16 | 1),
                       "1=1/1/1/t");

    document::BucketId id(16, 1);
    auto op = createOpWithDefaultConfig(
            createVisitorCommand(
                "multinodefailurecritical",
                id,
                nullId,
                8,
                500,
                inconsistent));

    op->start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL("Visitor Create => 0,Visitor Create => 1"s,
                         _sender.getCommands(true));
    return op;
}

void
VisitorOperationTest::testNoClientReplyBeforeAllStorageRepliesReceived()
{
    auto op = startOperationWith2StorageNodeVisitors(false);

    sendReply(*op, 0, api::ReturnCode::BUSY);
    // We don't want to see a reply here until the other node has replied.
    CPPUNIT_ASSERT_EQUAL(""s, _sender.getReplies(true));
    // OK reply from 1, but have to retry from client anyhow since one of
    // the sub buckets failed to be processed and we don't have inconsistent
    // visiting set in the client visitor command.
    sendReply(*op, 1);
    CPPUNIT_ASSERT_EQUAL(
            "CreateVisitorReply(last=BucketId(0x0000000000000000)) "
            "ReturnCode(BUCKET_NOT_FOUND)"s,
            _sender.getLastReply());
    // XXX we should consider wether we want BUSY to be returned instead.
    // Non-critical error codes are currently converted to a generic "not found"
    // code to let the client silently retry until the bucket has hopefully
    // become consistent/available.
}

void
VisitorOperationTest::testSkipFailedSubBucketsWhenVisitingInconsistent()
{
    auto op = startOperationWith2StorageNodeVisitors(true);

    sendReply(*op, 0, api::ReturnCode::BUSY);
    CPPUNIT_ASSERT_EQUAL(""s, _sender.getReplies(true));
    // Subset of buckets could not be visited, but visit inconsistent flag is
    // set in the client visitor so we treat it as a success anyway. In this
    // case we've expanded the entire superbucket sub-tree so return with magic
    // number to signify this.
    sendReply(*op, 1);
    CPPUNIT_ASSERT_EQUAL(
            "CreateVisitorReply(last=BucketId(0x000000007fffffff)) "
            "ReturnCode(NONE)"s,
            _sender.getLastReply());
}

// By default, queue timeout should be half of remaining visitor time. This
// is a highly un-scientific heuristic, but seems rather more reasonable than
// having it hard-coded to 2000 ms as was the case earlier.
void
VisitorOperationTest::testQueueTimeoutIsFactorOfTotalTimeout()
{
    document::BucketId id(uint64_t(0x400000000000007b));
    enableDistributorClusterState("distributor:1 storage:2");
    addNodesToBucketDB(id, "0=1/1/1/t,1=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("foo", id, nullId, 8, 10000));

    op->start(_sender, framework::MilliSecTime(0));
    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));

    auto& cmd(dynamic_cast<CreateVisitorCommand&>(*_sender.commands[0]));
    CPPUNIT_ASSERT_EQUAL(uint32_t(5000), cmd.getQueueTimeout());
}

void
VisitorOperationTest::do_visitor_roundtrip_with_statistics(
        const api::ReturnCode& result)
{
    document::BucketId id(0x400000000000007bULL);
    enableDistributorClusterState("distributor:1 storage:1");
    addNodesToBucketDB(id, "0=1/1/1/t");

    auto op = createOpWithDefaultConfig(
            createVisitorCommand("metricstats", id, nullId));

    op->start(_sender, framework::MilliSecTime(0));
    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create => 0"),
                         _sender.getCommands(true));
    auto& cmd(dynamic_cast<CreateVisitorCommand&>(*_sender.commands[0]));
    auto reply = cmd.makeReply();
    vdslib::VisitorStatistics stats;
    stats.setBucketsVisited(50);
    stats.setDocumentsVisited(100);
    stats.setBytesVisited(2000);
    static_cast<CreateVisitorReply&>(*reply).setVisitorStatistics(stats);
    reply->setResult(result);

    op->receive(_sender, api::StorageReply::SP(std::move(reply)));
}

void
VisitorOperationTest::metrics_are_updated_with_visitor_statistics_upon_replying()
{
    do_visitor_roundtrip_with_statistics(api::ReturnCode(api::ReturnCode::OK));

    CPPUNIT_ASSERT_EQUAL(int64_t(50), defaultVisitorMetrics().buckets_per_visitor.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(100), defaultVisitorMetrics().docs_per_visitor.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(2000), defaultVisitorMetrics().bytes_per_visitor.getLast());
}

void
VisitorOperationTest::statistical_metrics_not_updated_on_wrong_distribution()
{
    setupDistributor(1, 100, "distributor:100 storage:2");

    document::BucketId id(uint64_t(0x400000000000127b));
    CPPUNIT_ASSERT_EQUAL(
            std::string("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
                        "ReturnCode(WRONG_DISTRIBUTION, distributor:100 storage:2)"),
            runEmptyVisitor(createVisitorCommand("wrongdist", id, nullId)));

    // Note that we're testing the number of _times_ the metric has been
    // updated, not the value with which it's been updated (which would be zero
    // even in the case we actually did update the statistical metrics).
    CPPUNIT_ASSERT_EQUAL(int64_t(0), defaultVisitorMetrics().buckets_per_visitor.getCount());
    CPPUNIT_ASSERT_EQUAL(int64_t(0), defaultVisitorMetrics().docs_per_visitor.getCount());
    CPPUNIT_ASSERT_EQUAL(int64_t(0), defaultVisitorMetrics().bytes_per_visitor.getCount());
    // Fascinating that count is also a double...
    CPPUNIT_ASSERT_EQUAL(0.0, defaultVisitorMetrics().latency.getCount());
}

}

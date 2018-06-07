// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/persistence.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/storageserver/changedbucketownershiphandler.h>

using document::test::makeDocumentBucket;

namespace storage {

class ChangedBucketOwnershipHandlerTest : public CppUnit::TestFixture
{
    std::unique_ptr<TestServiceLayerApp> _app;
    std::unique_ptr<DummyStorageLink> _top;
    ChangedBucketOwnershipHandler* _handler;
    DummyStorageLink* _bottom;
    document::TestDocMan _testDocRepo;

    CPPUNIT_TEST_SUITE(ChangedBucketOwnershipHandlerTest);
    CPPUNIT_TEST(testEnumerateBucketsBelongingOnChangedNodes);
    CPPUNIT_TEST(testNoPreExistingClusterState);
    CPPUNIT_TEST(testNoAvailableDistributorsInCurrentState);
    CPPUNIT_TEST(testNoAvailableDistributorsInCurrentAndNewState);
    CPPUNIT_TEST(testDownEdgeToNoAvailableDistributors);
    CPPUNIT_TEST(testOwnershipChangedOnDistributorUpEdge);
    CPPUNIT_TEST(testDistributionConfigChangeUpdatesOwnership);
    CPPUNIT_TEST(testAbortOpsWhenNoClusterStateSet);
    CPPUNIT_TEST(testAbortOutdatedSplit);
    CPPUNIT_TEST(testAbortOutdatedJoin);
    CPPUNIT_TEST(testAbortOutdatedSetBucketState);
    CPPUNIT_TEST(testAbortOutdatedCreateBucket);
    CPPUNIT_TEST(testAbortOutdatedDeleteBucket);
    CPPUNIT_TEST(testAbortOutdatedMergeBucket);
    CPPUNIT_TEST(testAbortOutdatedRemoveLocation);
    CPPUNIT_TEST(testIdealStateAbortsAreConfigurable);
    CPPUNIT_TEST(testAbortOutdatedPutOperation);
    CPPUNIT_TEST(testAbortOutdatedUpdateCommand);
    CPPUNIT_TEST(testAbortOutdatedRemoveCommand);
    CPPUNIT_TEST(testAbortOutdatedRevertCommand);
    CPPUNIT_TEST(testIdealStateAbortUpdatesMetric);
    CPPUNIT_TEST(testExternalLoadOpAbortUpdatesMetric);
    CPPUNIT_TEST(testExternalLoadOpAbortsAreConfigurable);
    CPPUNIT_TEST(testAbortCommandsWhenStorageNodeIsDown);
    CPPUNIT_TEST_SUITE_END();

    // TODO test: down edge triggered on cluster state with cluster down?

    std::vector<document::BucketId> insertBuckets(
            uint32_t numBuckets,
            uint16_t wantedOwner,
            const lib::ClusterState& state);

    std::shared_ptr<api::SetSystemStateCommand> createStateCmd(
            const lib::ClusterState& state) const
    {
        return std::make_shared<api::SetSystemStateCommand>(state);
    }

    std::shared_ptr<api::SetSystemStateCommand> createStateCmd(
            const std::string& stateStr) const
    {
        return createStateCmd(lib::ClusterState(stateStr));
    }

    void applyDistribution(Redundancy, NodeCount);
    void applyClusterState(const lib::ClusterState&);

    document::BucketId nextOwnedBucket(
            uint16_t wantedOwner,
            const lib::ClusterState& state,
            const document::BucketId& lastId) const;

    document::Bucket getBucketToAbort() const;
    document::Bucket getBucketToAllow() const;

    void sendAndExpectAbortedCreateBucket(uint16_t fromDistributorIndex);

    template <typename MsgType, typename... MsgParams>
    bool changeAbortsMessage(MsgParams&&... params);

    template <typename MsgType, typename... MsgParams>
    bool downAbortsMessage(MsgParams&&... params);

    lib::ClusterState getDefaultTestClusterState() const {
        return lib::ClusterState("distributor:4 storage:1");
    }

    lib::ClusterState getStorageDownTestClusterState() const {
        return lib::ClusterState("distributor:4 storage:1 .0.s:d");
    }
public:
    void testEnumerateBucketsBelongingOnChangedNodes();
    void testNoPreExistingClusterState();
    void testNoAvailableDistributorsInCurrentState();
    void testNoAvailableDistributorsInCurrentAndNewState();
    void testDownEdgeToNoAvailableDistributors();
    void testOwnershipChangedOnDistributorUpEdge();
    void testDistributionConfigChangeUpdatesOwnership();
    void testAbortOpsWhenNoClusterStateSet();
    void testAbortOutdatedSplit();
    void testAbortOutdatedJoin();
    void testAbortOutdatedSetBucketState();
    void testAbortOutdatedCreateBucket();
    void testAbortOutdatedDeleteBucket();
    void testAbortOutdatedMergeBucket();
    void testAbortOutdatedRemoveLocation();
    void testIdealStateAbortsAreConfigurable();
    void testAbortOutdatedPutOperation();
    void testAbortOutdatedUpdateCommand();
    void testAbortOutdatedRemoveCommand();
    void testAbortOutdatedRevertCommand();
    void testIdealStateAbortUpdatesMetric();
    void testExternalLoadOpAbortUpdatesMetric();
    void testExternalLoadOpAbortsAreConfigurable();
    void testAbortCommandsWhenStorageNodeIsDown();

    void setUp() override;
};

CPPUNIT_TEST_SUITE_REGISTRATION(ChangedBucketOwnershipHandlerTest);

document::BucketId
ChangedBucketOwnershipHandlerTest::nextOwnedBucket(
        uint16_t wantedOwner,
        const lib::ClusterState& state,
        const document::BucketId& lastId) const
{
    uint32_t idx(lastId.getId() + 1); 
    while (true) {
        document::BucketId candidate(16, idx);
        uint16_t owner(_app->getDistribution()->getIdealDistributorNode(
                state, candidate));
        if (owner == wantedOwner) {
            return candidate;
        }
        ++idx;
    }
    assert(!"should never get here");
}

std::vector<document::BucketId>
ChangedBucketOwnershipHandlerTest::insertBuckets(uint32_t numBuckets,
                                                 uint16_t wantedOwner,
                                                 const lib::ClusterState& state)
{
    std::vector<document::BucketId> inserted;
    document::BucketId bucket;
    while (inserted.size() < numBuckets) {
        bucket = nextOwnedBucket(wantedOwner, state, bucket);

        bucketdb::StorageBucketInfo sbi;
        sbi.setBucketInfo(api::BucketInfo(1, 2, 3));
        sbi.disk = 0;
        _app->getStorageBucketDatabase().insert(bucket, sbi, "test");
        inserted.push_back(bucket);
    }
    return inserted;
}

void
ChangedBucketOwnershipHandlerTest::setUp()
{
    vdstestlib::DirConfig config(getStandardConfig(true));

    _app.reset(new TestServiceLayerApp);
    _top.reset(new DummyStorageLink);
    _handler = new ChangedBucketOwnershipHandler(config.getConfigId(),
                                                 _app->getComponentRegister());
    _top->push_back(std::unique_ptr<StorageLink>(_handler));
    _bottom = new DummyStorageLink;
    _handler->push_back(std::unique_ptr<StorageLink>(_bottom));
    _top->open();

    // Ensure we're not dependent on config schema default values.
    std::unique_ptr<vespa::config::content::PersistenceConfigBuilder> pconfig(
        new vespa::config::content::PersistenceConfigBuilder);
    pconfig->abortOutdatedMutatingIdealStateOps = true;
    pconfig->abortOutdatedMutatingExternalLoadOps = true;
    _handler->configure(std::move(pconfig));
}

namespace {

template <typename Set, typename K>
bool has(const Set& s, const K& key) {
    return s.find(key) != s.end();
}

template <typename Vec>
bool
hasAbortedAllOf(const AbortBucketOperationsCommand::SP& cmd, const Vec& v)
{
    for (auto& b : v) {
        if (!cmd->shouldAbort(makeDocumentBucket(b))) {
            return false;
        }
    }
    return true;
}

template <typename Vec>
bool
hasAbortedNoneOf(const AbortBucketOperationsCommand::SP& cmd, const Vec& v)
{
    for (auto& b : v) {
        if (cmd->shouldAbort(makeDocumentBucket(b))) {
            return false;
        }
    }
    return true;
}

bool
hasOnlySetSystemStateCmdQueued(DummyStorageLink& link) {
    if (link.getNumCommands() != 1) {
        std::cerr << "expected 1 command, found"
                  << link.getNumCommands() << "\n";
    }
    api::SetSystemStateCommand::SP cmd(
            std::dynamic_pointer_cast<api::SetSystemStateCommand>(
                    link.getCommand(0)));
    return (cmd.get() != 0);
}

}

void
ChangedBucketOwnershipHandlerTest::applyDistribution(
    Redundancy redundancy, NodeCount nodeCount)
{
    _app->setDistribution(redundancy, nodeCount);
    _handler->storageDistributionChanged();
}

void
ChangedBucketOwnershipHandlerTest::applyClusterState(
        const lib::ClusterState& state)
{
    _app->setClusterState(state);
    _handler->reloadClusterState();
}

void
ChangedBucketOwnershipHandlerTest::testEnumerateBucketsBelongingOnChangedNodes()
{
    lib::ClusterState stateBefore("distributor:4 storage:1");
    applyDistribution(Redundancy(1), NodeCount(4));
    applyClusterState(stateBefore);
    auto node1Buckets(insertBuckets(2, 1, stateBefore));
    auto node3Buckets(insertBuckets(2, 3, stateBefore));
    // Add some buckets that will not be part of the change set
    auto node0Buckets(insertBuckets(3, 0, stateBefore));
    auto node2Buckets(insertBuckets(2, 2, stateBefore));
    
    _top->sendDown(createStateCmd("distributor:4 .1.s:d .3.s:d storage:1"));
    // TODO: refactor into own function
    CPPUNIT_ASSERT_EQUAL(size_t(2), _bottom->getNumCommands());
    AbortBucketOperationsCommand::SP cmd(
            std::dynamic_pointer_cast<AbortBucketOperationsCommand>(
                    _bottom->getCommand(0)));
    CPPUNIT_ASSERT(cmd.get() != 0);

    CPPUNIT_ASSERT(hasAbortedAllOf(cmd, node1Buckets));
    CPPUNIT_ASSERT(hasAbortedAllOf(cmd, node3Buckets));
    CPPUNIT_ASSERT(hasAbortedNoneOf(cmd, node0Buckets));
    CPPUNIT_ASSERT(hasAbortedNoneOf(cmd, node2Buckets));

    // Handler must swallow abort replies
    _bottom->sendUp(api::StorageMessage::SP(cmd->makeReply().release()));
    CPPUNIT_ASSERT_EQUAL(size_t(0), _top->getNumReplies());
}

void
ChangedBucketOwnershipHandlerTest::testNoPreExistingClusterState()
{
    applyDistribution(Redundancy(1), NodeCount(4));
    lib::ClusterState stateBefore("distributor:4 storage:1");
    insertBuckets(2, 1, stateBefore);
    insertBuckets(3, 0, stateBefore);
    insertBuckets(2, 2, stateBefore);
    
    _top->sendDown(createStateCmd("distributor:4 .1.s:d .3.s:d storage:1"));
    CPPUNIT_ASSERT(hasOnlySetSystemStateCmdQueued(*_bottom));
}

/**
 * When current state has no distributors and we receive a state with one or
 * more distributors, we do not send any abort messages since this should
 * already have been done on the down-edge.
 */
void
ChangedBucketOwnershipHandlerTest::testNoAvailableDistributorsInCurrentState()
{
    applyDistribution(Redundancy(1), NodeCount(3));
    lib::ClusterState insertedState("distributor:3 storage:1");
    insertBuckets(2, 0, insertedState);
    insertBuckets(2, 1, insertedState);
    insertBuckets(2, 2, insertedState);
    lib::ClusterState downState("distributor:3 .0.s:d .1.s:d .2.s:d storage:1");
    _app->setClusterState(downState);

    _top->sendDown(createStateCmd("distributor:3 .1.s:d storage:1"));
    CPPUNIT_ASSERT(hasOnlySetSystemStateCmdQueued(*_bottom));
}

void
ChangedBucketOwnershipHandlerTest::testNoAvailableDistributorsInCurrentAndNewState()
{
    applyDistribution(Redundancy(1), NodeCount(3));
    lib::ClusterState insertedState("distributor:3 storage:1");
    insertBuckets(2, 0, insertedState);
    insertBuckets(2, 1, insertedState);
    insertBuckets(2, 2, insertedState);
    lib::ClusterState stateBefore("distributor:3 .0.s:s .1.s:s .2.s:d storage:1");
    applyClusterState(stateBefore);
    lib::ClusterState downState("distributor:3 .0.s:d .1.s:d .2.s:d storage:1");

    _top->sendDown(createStateCmd(downState));
    CPPUNIT_ASSERT(hasOnlySetSystemStateCmdQueued(*_bottom));
}

void
ChangedBucketOwnershipHandlerTest::testDownEdgeToNoAvailableDistributors()
{
    lib::ClusterState insertedState("distributor:3 storage:1");
    applyDistribution(Redundancy(1), NodeCount(3));
    applyClusterState(insertedState);
    auto node0Buckets(insertBuckets(2, 0, insertedState));
    auto node1Buckets(insertBuckets(2, 1, insertedState));
    auto node2Buckets(insertBuckets(2, 2, insertedState));
    lib::ClusterState downState("distributor:3 .0.s:d .1.s:s .2.s:s storage:1");

    _top->sendDown(createStateCmd(downState));
    // TODO: refactor into own function
    CPPUNIT_ASSERT_EQUAL(size_t(2), _bottom->getNumCommands());
    AbortBucketOperationsCommand::SP cmd(
            std::dynamic_pointer_cast<AbortBucketOperationsCommand>(
                    _bottom->getCommand(0)));
    CPPUNIT_ASSERT(cmd.get() != 0);

    CPPUNIT_ASSERT(hasAbortedAllOf(cmd, node0Buckets));
    CPPUNIT_ASSERT(hasAbortedAllOf(cmd, node1Buckets));
    CPPUNIT_ASSERT(hasAbortedAllOf(cmd, node2Buckets));
}

void
ChangedBucketOwnershipHandlerTest::testOwnershipChangedOnDistributorUpEdge()
{
    lib::ClusterState stateBefore(
            "version:10 distributor:4 .1.s:d storage:4 .1.s:d");
    lib::ClusterState stateAfter(
            "version:11 distributor:4 .1.t:1369990247 storage:4 .1.s:d");
    applyDistribution(Redundancy(1), NodeCount(4));
    applyClusterState(stateBefore);
    // Add buckets that will belong to distributor 1 after it has come back up
    auto node1Buckets(insertBuckets(2, 1, stateAfter));
    // Add some buckets that will not be part of the change set
    auto node0Buckets(insertBuckets(3, 0, stateAfter));
    auto node2Buckets(insertBuckets(2, 2, stateAfter));
    
    _top->sendDown(createStateCmd(stateAfter));
    // TODO: refactor into own function
    CPPUNIT_ASSERT_EQUAL(size_t(2), _bottom->getNumCommands());
    AbortBucketOperationsCommand::SP cmd(
            std::dynamic_pointer_cast<AbortBucketOperationsCommand>(
                    _bottom->getCommand(0)));
    CPPUNIT_ASSERT(cmd.get() != 0);

    CPPUNIT_ASSERT(hasAbortedAllOf(cmd, node1Buckets));
    CPPUNIT_ASSERT(hasAbortedNoneOf(cmd, node0Buckets));
    CPPUNIT_ASSERT(hasAbortedNoneOf(cmd, node2Buckets));

    // Handler must swallow abort replies
    _bottom->sendUp(api::StorageMessage::SP(cmd->makeReply().release()));
    CPPUNIT_ASSERT_EQUAL(size_t(0), _top->getNumReplies());
}

void
ChangedBucketOwnershipHandlerTest::sendAndExpectAbortedCreateBucket(
        uint16_t fromDistributorIndex)
{
    document::BucketId bucket(16, 6786);
    auto msg = std::make_shared<api::CreateBucketCommand>(makeDocumentBucket(bucket));
    msg->setSourceIndex(fromDistributorIndex);

    _top->sendDown(msg);
    std::vector<api::StorageMessage::SP> replies(_top->getRepliesOnce());
    CPPUNIT_ASSERT_EQUAL(size_t(1), replies.size());
    api::StorageReply& reply(dynamic_cast<api::StorageReply&>(*replies[0]));
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode::ABORTED,
                         reply.getResult().getResult());
}

void
ChangedBucketOwnershipHandlerTest::testAbortOpsWhenNoClusterStateSet()
{
    sendAndExpectAbortedCreateBucket(1);
}

void
ChangedBucketOwnershipHandlerTest::testDistributionConfigChangeUpdatesOwnership()
{
    lib::ClusterState insertedState("distributor:3 storage:1");
    applyClusterState(insertedState);
    applyDistribution(Redundancy(1), NodeCount(3));

    // Apply new distribution config containing only 1 distributor, meaning
    // any messages sent from >1 must be aborted.
    applyDistribution(Redundancy(1), NodeCount(1));
    sendAndExpectAbortedCreateBucket(2);
}

/**
 * Generate and dispatch a message of the given type with the provided
 * aruments as if that message was sent from distributor 1. Messages will
 * be checked as if the state contains 4 distributors in Up state. This
 * means that it suffices to send in a message with a bucket that is not
 * owned by distributor 1 in this state to trigger an abort.
 */
template <typename MsgType, typename... MsgParams>
bool
ChangedBucketOwnershipHandlerTest::changeAbortsMessage(MsgParams&&... params)
{
    auto msg = std::make_shared<MsgType>(std::forward<MsgParams>(params)...);
    msg->setSourceIndex(1);

    applyDistribution(Redundancy(1), NodeCount(4));
    applyClusterState(getDefaultTestClusterState());

    _top->sendDown(msg);
    std::vector<api::StorageMessage::SP> replies(_top->getRepliesOnce());
    // Test is single-threaded, no need to do any waiting.
    if (replies.empty()) {
        return false;
    } else {
        CPPUNIT_ASSERT_EQUAL(size_t(1), replies.size());
        // Make sure the message was actually aborted and not bounced with
        // some other arbitrary failure code.
        api::StorageReply& reply(dynamic_cast<api::StorageReply&>(*replies[0]));
        CPPUNIT_ASSERT_EQUAL(api::ReturnCode::ABORTED,
                             reply.getResult().getResult());
        return true;
    }
}

/**
 * Generate and dispatch a message of the given type with the provided
 * aruments as if that message was sent from distributor 1. Messages will
 * be checked as if the state contains 4 distributors in Up state and storage
 * node is down. This means that any abortable message will trigger an abort.
 */
template <typename MsgType, typename... MsgParams>
bool
ChangedBucketOwnershipHandlerTest::downAbortsMessage(MsgParams&&... params)
{
    (void) _top->getRepliesOnce();
    (void) _bottom->getCommandsOnce();
    CPPUNIT_ASSERT((!changeAbortsMessage<MsgType, MsgParams...>(std::forward<MsgParams>(params) ...)));
    _top->sendDown(createStateCmd(getStorageDownTestClusterState()));
    CPPUNIT_ASSERT_EQUAL(size_t(3), _bottom->getNumCommands());
    auto setSystemStateCommand = std::dynamic_pointer_cast<api::SetSystemStateCommand>(_bottom->getCommand(2));
    CPPUNIT_ASSERT(setSystemStateCommand);
    auto abortBucketOperationsCommand = std::dynamic_pointer_cast<AbortBucketOperationsCommand>(_bottom->getCommand(1));
    CPPUNIT_ASSERT(abortBucketOperationsCommand);
    auto testCommand = _bottom->getCommand(0);
    CPPUNIT_ASSERT(testCommand);
    return abortBucketOperationsCommand->shouldAbort(testCommand->getBucket());
}

/**
 * Returns a bucket that is not owned by the sending distributor (1). More
 * specifically, it returns a bucket that is owned by distributor 2.
 */
document::Bucket
ChangedBucketOwnershipHandlerTest::getBucketToAbort() const
{
    lib::ClusterState state(getDefaultTestClusterState());
    return makeDocumentBucket(nextOwnedBucket(2, state, document::BucketId()));
}

/**
 * Returns a bucket that _is_ owned by distributor 1 and should thus be
 * allowed through.
 */
document::Bucket
ChangedBucketOwnershipHandlerTest::getBucketToAllow() const
{
    lib::ClusterState state(getDefaultTestClusterState());
    return makeDocumentBucket(nextOwnedBucket(1, state, document::BucketId()));
}

void
ChangedBucketOwnershipHandlerTest::testAbortOutdatedSplit()
{
    CPPUNIT_ASSERT(changeAbortsMessage<api::SplitBucketCommand>(
            getBucketToAbort()));
    CPPUNIT_ASSERT(!changeAbortsMessage<api::SplitBucketCommand>(
            getBucketToAllow()));
}

void
ChangedBucketOwnershipHandlerTest::testAbortOutdatedJoin()
{
    CPPUNIT_ASSERT(changeAbortsMessage<api::JoinBucketsCommand>(
            getBucketToAbort()));
    CPPUNIT_ASSERT(!changeAbortsMessage<api::JoinBucketsCommand>(
            getBucketToAllow()));
}

void
ChangedBucketOwnershipHandlerTest::testAbortOutdatedSetBucketState()
{
    CPPUNIT_ASSERT(changeAbortsMessage<api::SetBucketStateCommand>(
            getBucketToAbort(), api::SetBucketStateCommand::ACTIVE));
    CPPUNIT_ASSERT(!changeAbortsMessage<api::SetBucketStateCommand>(
            getBucketToAllow(), api::SetBucketStateCommand::ACTIVE));
}

void
ChangedBucketOwnershipHandlerTest::testAbortOutdatedCreateBucket()
{
    CPPUNIT_ASSERT(changeAbortsMessage<api::CreateBucketCommand>(
            getBucketToAbort()));
    CPPUNIT_ASSERT(!changeAbortsMessage<api::CreateBucketCommand>(
            getBucketToAllow()));
}

void
ChangedBucketOwnershipHandlerTest::testAbortOutdatedDeleteBucket()
{
    CPPUNIT_ASSERT(changeAbortsMessage<api::DeleteBucketCommand>(
            getBucketToAbort()));
    CPPUNIT_ASSERT(!changeAbortsMessage<api::DeleteBucketCommand>(
            getBucketToAllow()));
}

void
ChangedBucketOwnershipHandlerTest::testAbortOutdatedMergeBucket()
{
    std::vector<api::MergeBucketCommand::Node> nodes;
    CPPUNIT_ASSERT(changeAbortsMessage<api::MergeBucketCommand>(
            getBucketToAbort(), nodes, 0));
    CPPUNIT_ASSERT(!changeAbortsMessage<api::MergeBucketCommand>(
            getBucketToAllow(), nodes, 0));
}

/**
 * RemoveLocation is technically an external load class, but since it's also
 * used as the backing operation for GC we have to treat it as if it were an
 * ideal state operation class.
 */
void
ChangedBucketOwnershipHandlerTest::testAbortOutdatedRemoveLocation()
{
    std::vector<api::MergeBucketCommand::Node> nodes;
    CPPUNIT_ASSERT(changeAbortsMessage<api::RemoveLocationCommand>(
            "foo", getBucketToAbort()));
    CPPUNIT_ASSERT(!changeAbortsMessage<api::RemoveLocationCommand>(
            "foo", getBucketToAllow()));
}

void
ChangedBucketOwnershipHandlerTest::testIdealStateAbortsAreConfigurable()
{
    std::unique_ptr<vespa::config::content::PersistenceConfigBuilder> config(
        new vespa::config::content::PersistenceConfigBuilder);
    config->abortOutdatedMutatingIdealStateOps = false;
    _handler->configure(std::move(config));
    // Should not abort operation, even when ownership has changed.
    CPPUNIT_ASSERT(!changeAbortsMessage<api::CreateBucketCommand>(
            getBucketToAbort()));
}

void
ChangedBucketOwnershipHandlerTest::testAbortOutdatedPutOperation()
{
    document::Document::SP doc(_testDocRepo.createRandomDocumentAtLocation(1));
    CPPUNIT_ASSERT(changeAbortsMessage<api::PutCommand>(
            getBucketToAbort(), doc, api::Timestamp(1234)));
    CPPUNIT_ASSERT(!changeAbortsMessage<api::PutCommand>(
            getBucketToAllow(), doc, api::Timestamp(1234)));
}

void
ChangedBucketOwnershipHandlerTest::testAbortOutdatedUpdateCommand()
{
    const document::DocumentType* docType(_testDocRepo.getTypeRepo()
                                          .getDocumentType("testdoctype1"));
    document::DocumentId docId("id:foo:testdoctype1::bar");
    document::DocumentUpdate::SP update(
            std::make_shared<document::DocumentUpdate>(*docType, docId));
    CPPUNIT_ASSERT(changeAbortsMessage<api::UpdateCommand>(
            getBucketToAbort(), update, api::Timestamp(1234)));
    CPPUNIT_ASSERT(!changeAbortsMessage<api::UpdateCommand>(
            getBucketToAllow(), update, api::Timestamp(1234)));
}

void
ChangedBucketOwnershipHandlerTest::testAbortOutdatedRemoveCommand()
{
    document::DocumentId docId("id:foo:testdoctype1::bar");
    CPPUNIT_ASSERT(changeAbortsMessage<api::RemoveCommand>(
            getBucketToAbort(), docId, api::Timestamp(1234)));
    CPPUNIT_ASSERT(!changeAbortsMessage<api::RemoveCommand>(
            getBucketToAllow(), docId, api::Timestamp(1234)));
}

void
ChangedBucketOwnershipHandlerTest::testAbortOutdatedRevertCommand()
{
    std::vector<api::Timestamp> timestamps;
    CPPUNIT_ASSERT(changeAbortsMessage<api::RevertCommand>(
            getBucketToAbort(), timestamps));
    CPPUNIT_ASSERT(!changeAbortsMessage<api::RevertCommand>(
            getBucketToAllow(), timestamps));
}

void
ChangedBucketOwnershipHandlerTest::testIdealStateAbortUpdatesMetric()
{
    CPPUNIT_ASSERT(changeAbortsMessage<api::SplitBucketCommand>(
            getBucketToAbort()));
    CPPUNIT_ASSERT_EQUAL(
            uint64_t(1),
            _handler->getMetrics().idealStateOpsAborted.getValue());
    CPPUNIT_ASSERT_EQUAL(
            uint64_t(0),
            _handler->getMetrics().externalLoadOpsAborted.getValue());
}

void
ChangedBucketOwnershipHandlerTest::testExternalLoadOpAbortUpdatesMetric()
{
    document::DocumentId docId("id:foo:testdoctype1::bar");
    CPPUNIT_ASSERT(changeAbortsMessage<api::RemoveCommand>(
            getBucketToAbort(), docId, api::Timestamp(1234)));
    CPPUNIT_ASSERT_EQUAL(
            uint64_t(0),
            _handler->getMetrics().idealStateOpsAborted.getValue());
    CPPUNIT_ASSERT_EQUAL(
            uint64_t(1),
            _handler->getMetrics().externalLoadOpsAborted.getValue());
}

void
ChangedBucketOwnershipHandlerTest::testExternalLoadOpAbortsAreConfigurable()
{
    std::unique_ptr<vespa::config::content::PersistenceConfigBuilder> config(
        new vespa::config::content::PersistenceConfigBuilder);
    config->abortOutdatedMutatingExternalLoadOps = false;
    _handler->configure(std::move(config));
    // Should not abort operation, even when ownership has changed.
    document::DocumentId docId("id:foo:testdoctype1::bar");
    CPPUNIT_ASSERT(!changeAbortsMessage<api::RemoveCommand>(
            getBucketToAbort(), docId, api::Timestamp(1234)));
}

void
ChangedBucketOwnershipHandlerTest::testAbortCommandsWhenStorageNodeIsDown()
{
    document::Document::SP doc(_testDocRepo.createRandomDocumentAtLocation(1));
    CPPUNIT_ASSERT(downAbortsMessage<api::PutCommand>(
            getBucketToAllow(), doc, api::Timestamp(1234)));
    CPPUNIT_ASSERT(downAbortsMessage<api::SetBucketStateCommand>(
            getBucketToAllow(), api::SetBucketStateCommand::ACTIVE));
}

} // storage

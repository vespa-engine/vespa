// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/storageserver/changedbucketownershiphandler.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>

#include <vespa/vespalib/gtest/gtest.h>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

struct ChangedBucketOwnershipHandlerTest : Test {
    std::unique_ptr<TestServiceLayerApp> _app;
    std::unique_ptr<DummyStorageLink> _top;
    ChangedBucketOwnershipHandler* _handler;
    DummyStorageLink* _bottom;
    document::TestDocMan _testDocRepo;

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
    void expectChangeAbortsMessage(bool expected, MsgParams&& ... params);

    template <typename MsgType, typename... MsgParams>
    void expectDownAbortsMessage(bool expected, MsgParams&& ... params);

    lib::ClusterState getDefaultTestClusterState() const {
        return lib::ClusterState("distributor:4 storage:1");
    }

    lib::ClusterState getStorageDownTestClusterState() const {
        return lib::ClusterState("distributor:4 storage:1 .0.s:d");
    }

    void SetUp() override;
};

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
        _app->getStorageBucketDatabase().insert(bucket, sbi, "test");
        inserted.push_back(bucket);
    }
    return inserted;
}

void
ChangedBucketOwnershipHandlerTest::SetUp()
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
    auto pconfig = std::make_unique<vespa::config::content::PersistenceConfigBuilder>();
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

TEST_F(ChangedBucketOwnershipHandlerTest, enumerate_buckets_belonging_on_changed_nodes) {
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
    ASSERT_EQ(2, _bottom->getNumCommands());
    auto cmd = std::dynamic_pointer_cast<AbortBucketOperationsCommand>(_bottom->getCommand(0));
    ASSERT_TRUE(cmd.get() != 0);

    EXPECT_TRUE(hasAbortedAllOf(cmd, node1Buckets));
    EXPECT_TRUE(hasAbortedAllOf(cmd, node3Buckets));
    EXPECT_TRUE(hasAbortedNoneOf(cmd, node0Buckets));
    EXPECT_TRUE(hasAbortedNoneOf(cmd, node2Buckets));

    // Handler must swallow abort replies
    _bottom->sendUp(api::StorageMessage::SP(cmd->makeReply().release()));
    EXPECT_EQ(size_t(0), _top->getNumReplies());
}

TEST_F(ChangedBucketOwnershipHandlerTest, no_pre_existing_cluster_state) {
    applyDistribution(Redundancy(1), NodeCount(4));
    lib::ClusterState stateBefore("distributor:4 storage:1");
    insertBuckets(2, 1, stateBefore);
    insertBuckets(3, 0, stateBefore);
    insertBuckets(2, 2, stateBefore);
    
    _top->sendDown(createStateCmd("distributor:4 .1.s:d .3.s:d storage:1"));
    EXPECT_TRUE(hasOnlySetSystemStateCmdQueued(*_bottom));
}

/**
 * When current state has no distributors and we receive a state with one or
 * more distributors, we do not send any abort messages since this should
 * already have been done on the down-edge.
 */
TEST_F(ChangedBucketOwnershipHandlerTest, no_available_distributors_in_current_state) {
    applyDistribution(Redundancy(1), NodeCount(3));
    lib::ClusterState insertedState("distributor:3 storage:1");
    insertBuckets(2, 0, insertedState);
    insertBuckets(2, 1, insertedState);
    insertBuckets(2, 2, insertedState);
    lib::ClusterState downState("distributor:3 .0.s:d .1.s:d .2.s:d storage:1");
    _app->setClusterState(downState);

    _top->sendDown(createStateCmd("distributor:3 .1.s:d storage:1"));
    EXPECT_TRUE(hasOnlySetSystemStateCmdQueued(*_bottom));
}

TEST_F(ChangedBucketOwnershipHandlerTest, no_available_distributors_in_current_and_new_state) {
    applyDistribution(Redundancy(1), NodeCount(3));
    lib::ClusterState insertedState("distributor:3 storage:1");
    insertBuckets(2, 0, insertedState);
    insertBuckets(2, 1, insertedState);
    insertBuckets(2, 2, insertedState);
    lib::ClusterState stateBefore("distributor:3 .0.s:s .1.s:s .2.s:d storage:1");
    applyClusterState(stateBefore);
    lib::ClusterState downState("distributor:3 .0.s:d .1.s:d .2.s:d storage:1");

    _top->sendDown(createStateCmd(downState));
    EXPECT_TRUE(hasOnlySetSystemStateCmdQueued(*_bottom));
}

TEST_F(ChangedBucketOwnershipHandlerTest, down_edge_to_no_available_distributors) {
    lib::ClusterState insertedState("distributor:3 storage:1");
    applyDistribution(Redundancy(1), NodeCount(3));
    applyClusterState(insertedState);
    auto node0Buckets(insertBuckets(2, 0, insertedState));
    auto node1Buckets(insertBuckets(2, 1, insertedState));
    auto node2Buckets(insertBuckets(2, 2, insertedState));
    lib::ClusterState downState("distributor:3 .0.s:d .1.s:s .2.s:s storage:1");

    _top->sendDown(createStateCmd(downState));
    // TODO: refactor into own function
    ASSERT_EQ(2, _bottom->getNumCommands());
    auto cmd = std::dynamic_pointer_cast<AbortBucketOperationsCommand>(_bottom->getCommand(0));
    ASSERT_TRUE(cmd.get() != 0);

    EXPECT_TRUE(hasAbortedAllOf(cmd, node0Buckets));
    EXPECT_TRUE(hasAbortedAllOf(cmd, node1Buckets));
    EXPECT_TRUE(hasAbortedAllOf(cmd, node2Buckets));
}

TEST_F(ChangedBucketOwnershipHandlerTest, ownership_changed_on_distributor_up_edge) {
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
    ASSERT_EQ(2, _bottom->getNumCommands());
    auto cmd = std::dynamic_pointer_cast<AbortBucketOperationsCommand>(_bottom->getCommand(0));
    ASSERT_TRUE(cmd.get() != 0);

    EXPECT_TRUE(hasAbortedAllOf(cmd, node1Buckets));
    EXPECT_TRUE(hasAbortedNoneOf(cmd, node0Buckets));
    EXPECT_TRUE(hasAbortedNoneOf(cmd, node2Buckets));

    // Handler must swallow abort replies
    _bottom->sendUp(api::StorageMessage::SP(cmd->makeReply().release()));
    EXPECT_EQ(0, _top->getNumReplies());
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
    ASSERT_EQ(1, replies.size());
    auto& reply(dynamic_cast<api::StorageReply&>(*replies[0]));
    EXPECT_EQ(api::ReturnCode::ABORTED, reply.getResult().getResult());
}

TEST_F(ChangedBucketOwnershipHandlerTest, abort_ops_when_no_cluster_state_set) {
    sendAndExpectAbortedCreateBucket(1);
}

TEST_F(ChangedBucketOwnershipHandlerTest, distribution_config_change_updates_ownership) {
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
void
ChangedBucketOwnershipHandlerTest::expectChangeAbortsMessage(bool expected, MsgParams&&... params)
{
    auto msg = std::make_shared<MsgType>(std::forward<MsgParams>(params)...);
    msg->setSourceIndex(1);

    applyDistribution(Redundancy(1), NodeCount(4));
    applyClusterState(getDefaultTestClusterState());

    _top->sendDown(msg);
    std::vector<api::StorageMessage::SP> replies(_top->getRepliesOnce());
    // Test is single-threaded, no need to do any waiting.
    if (replies.empty()) {
        EXPECT_FALSE(expected);
    } else {
        ASSERT_EQ(replies.size(), 1);
        // Make sure the message was actually aborted and not bounced with
        // some other arbitrary failure code.
        auto& reply(dynamic_cast<api::StorageReply&>(*replies[0]));
        ASSERT_EQ(reply.getResult().getResult(), api::ReturnCode::ABORTED);
        EXPECT_TRUE(expected);
    }
}

/**
 * Generate and dispatch a message of the given type with the provided
 * aruments as if that message was sent from distributor 1. Messages will
 * be checked as if the state contains 4 distributors in Up state and storage
 * node is down. This means that any abortable message will trigger an abort.
 */
template <typename MsgType, typename... MsgParams>
void
ChangedBucketOwnershipHandlerTest::expectDownAbortsMessage(bool expected, MsgParams&&... params)
{
    (void) _top->getRepliesOnce();
    (void) _bottom->getCommandsOnce();
    ASSERT_NO_FATAL_FAILURE((expectChangeAbortsMessage<MsgType, MsgParams...>(false, std::forward<MsgParams>(params)...)));
    _top->sendDown(createStateCmd(getStorageDownTestClusterState()));
    ASSERT_EQ(_bottom->getNumCommands(), 3);
    auto setSystemStateCommand = std::dynamic_pointer_cast<api::SetSystemStateCommand>(_bottom->getCommand(2));
    ASSERT_TRUE(setSystemStateCommand);
    auto abortBucketOperationsCommand = std::dynamic_pointer_cast<AbortBucketOperationsCommand>(_bottom->getCommand(1));
    ASSERT_TRUE(abortBucketOperationsCommand);
    auto testCommand = _bottom->getCommand(0);
    ASSERT_TRUE(testCommand);
    EXPECT_EQ(expected, abortBucketOperationsCommand->shouldAbort(testCommand->getBucket()));
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

TEST_F(ChangedBucketOwnershipHandlerTest, abort_outdated_split) {
    expectChangeAbortsMessage<api::SplitBucketCommand>(true, getBucketToAbort());
    expectChangeAbortsMessage<api::SplitBucketCommand>(false, getBucketToAllow());
}

TEST_F(ChangedBucketOwnershipHandlerTest, abort_outdated_join) {
    expectChangeAbortsMessage<api::JoinBucketsCommand>(true, getBucketToAbort());
    expectChangeAbortsMessage<api::JoinBucketsCommand>(false, getBucketToAllow());
}

TEST_F(ChangedBucketOwnershipHandlerTest, abort_outdated_set_bucket_state) {
    expectChangeAbortsMessage<api::SetBucketStateCommand>(
            true, getBucketToAbort(), api::SetBucketStateCommand::ACTIVE);
    expectChangeAbortsMessage<api::SetBucketStateCommand>(
            false, getBucketToAllow(), api::SetBucketStateCommand::ACTIVE);
}

TEST_F(ChangedBucketOwnershipHandlerTest, abort_outdated_create_bucket) {
    expectChangeAbortsMessage<api::CreateBucketCommand>(true, getBucketToAbort());
    expectChangeAbortsMessage<api::CreateBucketCommand>(false, getBucketToAllow());
}

TEST_F(ChangedBucketOwnershipHandlerTest, abort_outdated_delete_bucket) {
    expectChangeAbortsMessage<api::DeleteBucketCommand>(true, getBucketToAbort());
    expectChangeAbortsMessage<api::DeleteBucketCommand>(false, getBucketToAllow());
}

TEST_F(ChangedBucketOwnershipHandlerTest, abort_outdated_merge_bucket) {
    std::vector<api::MergeBucketCommand::Node> nodes;
    expectChangeAbortsMessage<api::MergeBucketCommand>(true, getBucketToAbort(), nodes, 0);
    expectChangeAbortsMessage<api::MergeBucketCommand>(false, getBucketToAllow(), nodes, 0);
}

/**
 * RemoveLocation is technically an external load class, but since it's also
 * used as the backing operation for GC we have to treat it as if it were an
 * ideal state operation class.
 */
TEST_F(ChangedBucketOwnershipHandlerTest, abort_outdated_remove_location) {
    std::vector<api::MergeBucketCommand::Node> nodes;
    expectChangeAbortsMessage<api::RemoveLocationCommand>(true, "foo", getBucketToAbort());
    expectChangeAbortsMessage<api::RemoveLocationCommand>(false, "foo", getBucketToAllow());
}

TEST_F(ChangedBucketOwnershipHandlerTest, ideal_state_aborts_are_configurable) {
    auto config = std::make_unique<vespa::config::content::PersistenceConfigBuilder>();
    config->abortOutdatedMutatingIdealStateOps = false;
    _handler->configure(std::move(config));
    // Should not abort operation, even when ownership has changed.
    expectChangeAbortsMessage<api::CreateBucketCommand>(false, getBucketToAbort());
}

TEST_F(ChangedBucketOwnershipHandlerTest, abort_outdated_put_operation) {
    document::Document::SP doc(_testDocRepo.createRandomDocumentAtLocation(1));
    expectChangeAbortsMessage<api::PutCommand>(true, getBucketToAbort(), doc, api::Timestamp(1234));
    expectChangeAbortsMessage<api::PutCommand>(false, getBucketToAllow(), doc, api::Timestamp(1234));
}

TEST_F(ChangedBucketOwnershipHandlerTest, abort_outdated_update_command) {
    const document::DocumentType* docType(_testDocRepo.getTypeRepo().getDocumentType("testdoctype1"));
    document::DocumentId docId("id:foo:testdoctype1::bar");
    auto update(std::make_shared<document::DocumentUpdate>(_testDocRepo.getTypeRepo(), *docType, docId));
    expectChangeAbortsMessage<api::UpdateCommand>(true, getBucketToAbort(), update, api::Timestamp(1234));
    expectChangeAbortsMessage<api::UpdateCommand>(false, getBucketToAllow(), update, api::Timestamp(1234));
}

TEST_F(ChangedBucketOwnershipHandlerTest, abort_outdated_remove_command) {
    document::DocumentId docId("id:foo:testdoctype1::bar");
    expectChangeAbortsMessage<api::RemoveCommand>(true, getBucketToAbort(), docId, api::Timestamp(1234));
    expectChangeAbortsMessage<api::RemoveCommand>(false, getBucketToAllow(), docId, api::Timestamp(1234));
}

TEST_F(ChangedBucketOwnershipHandlerTest, abort_outdated_revert_command) {
    std::vector<api::Timestamp> timestamps;
    expectChangeAbortsMessage<api::RevertCommand>(true, getBucketToAbort(), timestamps);
    expectChangeAbortsMessage<api::RevertCommand>(false, getBucketToAllow(), timestamps);
}

TEST_F(ChangedBucketOwnershipHandlerTest, ideal_state_abort_updates_metric) {
    expectChangeAbortsMessage<api::SplitBucketCommand>(true, getBucketToAbort());
    EXPECT_EQ(1, _handler->getMetrics().idealStateOpsAborted.getValue());
    EXPECT_EQ(0, _handler->getMetrics().externalLoadOpsAborted.getValue());
}

TEST_F(ChangedBucketOwnershipHandlerTest, external_load_op_abort_updates_metric) {
    document::DocumentId docId("id:foo:testdoctype1::bar");
    expectChangeAbortsMessage<api::RemoveCommand>(
            true, getBucketToAbort(), docId, api::Timestamp(1234));
    EXPECT_EQ(0, _handler->getMetrics().idealStateOpsAborted.getValue());
    EXPECT_EQ(1, _handler->getMetrics().externalLoadOpsAborted.getValue());
}

TEST_F(ChangedBucketOwnershipHandlerTest, external_load_op_aborts_are_configurable) {
    auto config = std::make_unique<vespa::config::content::PersistenceConfigBuilder>();
    config->abortOutdatedMutatingExternalLoadOps = false;
    _handler->configure(std::move(config));
    // Should not abort operation, even when ownership has changed.
    document::DocumentId docId("id:foo:testdoctype1::bar");
    expectChangeAbortsMessage<api::RemoveCommand>(
            false, getBucketToAbort(), docId, api::Timestamp(1234));
}

TEST_F(ChangedBucketOwnershipHandlerTest, abort_commands_when_storage_node_is_down) {
    document::Document::SP doc(_testDocRepo.createRandomDocumentAtLocation(1));
    expectDownAbortsMessage<api::PutCommand>(
            true, getBucketToAllow(), doc, api::Timestamp(1234));
    expectDownAbortsMessage<api::SetBucketStateCommand>(
            true, getBucketToAllow(), api::SetBucketStateCommand::ACTIVE);
}

} // storage

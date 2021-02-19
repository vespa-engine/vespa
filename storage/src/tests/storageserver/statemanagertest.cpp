// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/metrics/metricmanager.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storage/storageserver/statemanager.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

using storage::lib::NodeState;
using storage::lib::NodeType;
using storage::lib::State;
using storage::lib::ClusterState;
using namespace ::testing;

namespace storage {

struct StateManagerTest : Test {
    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<DummyStorageLink> _upper;
    std::unique_ptr<metrics::MetricManager> _metricManager;
    StateManager* _manager;
    DummyStorageLink* _lower;

    StateManagerTest();

    void SetUp() override;
    void TearDown() override;

    void force_current_cluster_state_version(uint32_t version);
    void mark_reported_node_state_up();
    void send_down_get_node_state_request(uint16_t controller_index);
    void assert_ok_get_node_state_reply_sent_and_clear();
    void clear_sent_replies();
    void mark_reply_observed_from_n_controllers(uint16_t n);

    std::string get_node_info() const {
        return _manager->getNodeInfo();
    }
};

StateManagerTest::StateManagerTest()
    : _node(),
      _upper(),
      _manager(nullptr),
      _lower(nullptr)
{
}

void
StateManagerTest::SetUp() {
    vdstestlib::DirConfig config(getStandardConfig(true));
    _node = std::make_unique<TestServiceLayerApp>(NodeIndex(2));
    // Clock will increase 1 sec per call.
    _node->getClock().setAbsoluteTimeInSeconds(1);
    _metricManager = std::make_unique<metrics::MetricManager>();
    _upper = std::make_unique<DummyStorageLink>();
    _manager = new StateManager(_node->getComponentRegister(),
                                *_metricManager,
                                std::make_unique<HostInfo>());
    _lower = new DummyStorageLink();
    _upper->push_back(StorageLink::UP(_manager));
    _upper->push_back(StorageLink::UP(_lower));
    _upper->open();
}

void
StateManagerTest::TearDown() {
    assert(_lower->getNumReplies() == 0);
    assert(_lower->getNumCommands() == 0);
    assert(_upper->getNumReplies() == 0);
    assert(_upper->getNumCommands() == 0);
    _manager = nullptr;
    _lower = nullptr;
    _upper->close();
    _upper->flush();
    _upper.reset();
    _node.reset();
    _metricManager.reset();
}

void StateManagerTest::force_current_cluster_state_version(uint32_t version) {
    ClusterState state(*_manager->getClusterStateBundle()->getBaselineClusterState());
    state.setVersion(version);
    _manager->setClusterStateBundle(lib::ClusterStateBundle(state));
}

#define GET_ONLY_OK_REPLY(varname) \
{ \
    ASSERT_EQ(size_t(1), _upper->getNumReplies()); \
    ASSERT_TRUE(_upper->getReply(0)->getType().isReply()); \
    varname = std::dynamic_pointer_cast<api::StorageReply>( \
                    _upper->getReply(0)); \
    ASSERT_TRUE(varname.get() != nullptr); \
    _upper->reset(); \
    ASSERT_EQ(api::ReturnCode(api::ReturnCode::OK), \
              varname->getResult()); \
}

TEST_F(StateManagerTest, cluster_state) {
    std::shared_ptr<api::StorageReply> reply;
    // Verify initial state on startup
    auto currentState = _manager->getClusterStateBundle()->getBaselineClusterState();
    EXPECT_EQ("cluster:d", currentState->toString(false));

    auto currentNodeState = _manager->getCurrentNodeState();
    EXPECT_EQ("s:d", currentNodeState->toString(false));

    ClusterState sendState("storage:4 .2.s:m");
    auto cmd = std::make_shared<api::SetSystemStateCommand>(sendState);
    _upper->sendDown(cmd);
    GET_ONLY_OK_REPLY(reply);

    currentState = _manager->getClusterStateBundle()->getBaselineClusterState();
    EXPECT_EQ(sendState, *currentState);

    currentNodeState = _manager->getCurrentNodeState();
    EXPECT_EQ("s:m", currentNodeState->toString(false));
}

namespace {
struct MyStateListener : public StateListener {
    const NodeStateUpdater& updater;
    lib::NodeState current;
    std::ostringstream ost;

    MyStateListener(const NodeStateUpdater& upd)
        : updater(upd), current(*updater.getReportedNodeState()) {}
    ~MyStateListener() override = default;

    void handleNewState() override {
        ost << current << " -> ";
        current = *updater.getReportedNodeState();
        ost << current << "\n";
    }
};
}

TEST_F(StateManagerTest, reported_node_state) {
    std::shared_ptr<api::StorageReply> reply;
    // Add a state listener to check that we get events.
    MyStateListener stateListener(*_manager);
    _manager->addStateListener(stateListener);
    // Test that initial state is initializing
    auto nodeState = _manager->getReportedNodeState();
    EXPECT_EQ("s:i b:58 i:0 t:1", nodeState->toString(false));
    // Test that it works to update the state
    {
        auto lock = _manager->grabStateChangeLock();
        NodeState ns(*_manager->getReportedNodeState());
        ns.setState(State::UP);
        _manager->setReportedNodeState(ns);
    }
    // And that we get the change both through state interface
    nodeState = _manager->getReportedNodeState();
    EXPECT_EQ("s:u b:58 t:1", nodeState->toString(false));
    // And get node state command (no expected state)
    auto cmd = std::make_shared<api::GetNodeStateCommand>(lib::NodeState::UP());
    _upper->sendDown(cmd);
    GET_ONLY_OK_REPLY(reply);
    ASSERT_EQ(api::MessageType::GETNODESTATE_REPLY, reply->getType());
    nodeState = std::make_shared<NodeState>(
                dynamic_cast<api::GetNodeStateReply&>(*reply).getNodeState());
    EXPECT_EQ("s:u b:58 t:1", nodeState->toString(false));
    // We should also get it with wrong expected state
    cmd = std::make_shared<api::GetNodeStateCommand>(
            std::make_unique<NodeState>(NodeType::STORAGE, State::INITIALIZING));
    _upper->sendDown(cmd);
    GET_ONLY_OK_REPLY(reply);
    ASSERT_EQ(api::MessageType::GETNODESTATE_REPLY, reply->getType());
    nodeState = std::make_unique<NodeState>(
                dynamic_cast<api::GetNodeStateReply&>(*reply).getNodeState());
    EXPECT_EQ("s:u b:58 t:1", nodeState->toString(false));
    // With correct wanted state we should not get response right away
    cmd = std::make_shared<api::GetNodeStateCommand>(
            std::make_unique<lib::NodeState>("s:u b:58 t:1", &NodeType::STORAGE));
    _upper->sendDown(cmd);
    ASSERT_EQ(size_t(0), _upper->getNumReplies());
    // But when we update state, we get the reply
    {
        NodeStateUpdater::Lock::SP lock(_manager->grabStateChangeLock());
        NodeState ns(*_manager->getReportedNodeState());
        ns.setState(State::STOPPING);
        ns.setDescription("Stopping node");
        _manager->setReportedNodeState(ns);
    }

    GET_ONLY_OK_REPLY(reply);
    ASSERT_EQ(api::MessageType::GETNODESTATE_REPLY, reply->getType());
    nodeState = std::make_unique<NodeState>(
                dynamic_cast<api::GetNodeStateReply&>(*reply).getNodeState());
    EXPECT_EQ("s:s b:58 t:1 m:Stopping\\x20node", nodeState->toString(false));

    // Removing state listener, it stops getting updates
    _manager->removeStateListener(stateListener);
    // Do another update which listener should not get..
    {
        NodeStateUpdater::Lock::SP lock(_manager->grabStateChangeLock());
        NodeState ns(*_manager->getReportedNodeState());
        ns.setState(State::UP);
        _manager->setReportedNodeState(ns);
    }
    std::string expectedEvents =
            "s:i b:58 i:0 t:1 -> s:u b:58 t:1\n"
            "s:u b:58 t:1 -> s:s b:58 t:1 m:Stopping\\x20node\n";
    EXPECT_EQ(expectedEvents, stateListener.ost.str());
}

TEST_F(StateManagerTest, current_cluster_state_version_is_included_in_host_info_json) {
    force_current_cluster_state_version(123);

    std::string nodeInfoString = get_node_info();
    vespalib::Memory goldenMemory(nodeInfoString);
    vespalib::Slime nodeInfo;
    vespalib::slime::JsonFormat::decode(nodeInfoString, nodeInfo);
    
    vespalib::slime::Symbol lookupSymbol = nodeInfo.lookup("cluster-state-version");
    if (lookupSymbol.undefined()) {
        FAIL() << "No cluster-state-version was found in the node info";
    }

    auto& cursor = nodeInfo.get();
    auto& clusterStateVersionCursor = cursor["cluster-state-version"];
    if (!clusterStateVersionCursor.valid()) {
        FAIL() << "No cluster-state-version was found in the node info";
    }

    if (clusterStateVersionCursor.type().getId() != vespalib::slime::LONG::ID) {
        FAIL() << "No cluster-state-version was found in the node info";
    }

    int version = clusterStateVersionCursor.asLong();
    EXPECT_EQ(123, version);
}

void StateManagerTest::mark_reported_node_state_up() {
    auto lock = _manager->grabStateChangeLock();
    _manager->setReportedNodeState(NodeState(NodeType::STORAGE, State::UP));
}

void StateManagerTest::send_down_get_node_state_request(uint16_t controller_index) {
    auto cmd = std::make_shared<api::GetNodeStateCommand>(
            std::make_unique<NodeState>(NodeType::STORAGE, State::UP));
    cmd->setTimeout(10000000ms);
    cmd->setSourceIndex(controller_index);
    _upper->sendDown(cmd);
}

void StateManagerTest::assert_ok_get_node_state_reply_sent_and_clear() {
    ASSERT_EQ(1, _upper->getNumReplies());
    std::shared_ptr<api::StorageReply> reply;
    GET_ONLY_OK_REPLY(reply); // Implicitly clears messages from _upper
    ASSERT_EQ(api::MessageType::GETNODESTATE_REPLY, reply->getType());
}

void StateManagerTest::clear_sent_replies() {
    _upper->getRepliesOnce();
}

void StateManagerTest::mark_reply_observed_from_n_controllers(uint16_t n) {
    for (uint16_t i = 0; i < n; ++i) {
        send_down_get_node_state_request(i);
        ASSERT_NO_FATAL_FAILURE(assert_ok_get_node_state_reply_sent_and_clear());
    }
}

TEST_F(StateManagerTest, can_explicitly_send_get_node_state_reply) {
    mark_reported_node_state_up();
    // Must "pre-trigger" that a controller has already received a GetNodeState
    // reply, or an immediate reply will be sent by default when the first request
    // from a controller is observed.
    mark_reply_observed_from_n_controllers(1);

    send_down_get_node_state_request(0);
    ASSERT_EQ(0, _upper->getNumReplies());

    _manager->immediately_send_get_node_state_replies();
    ASSERT_NO_FATAL_FAILURE(assert_ok_get_node_state_reply_sent_and_clear());
}

TEST_F(StateManagerTest, explicit_node_state_replying_without_pending_request_immediately_replies_on_next_request) {
    mark_reported_node_state_up();
    mark_reply_observed_from_n_controllers(1);

    // No pending requests at this time
    _manager->immediately_send_get_node_state_replies();

    send_down_get_node_state_request(0);
    ASSERT_NO_FATAL_FAILURE(assert_ok_get_node_state_reply_sent_and_clear());
    // Sending a new request should now _not_ immediately receive a reply
    send_down_get_node_state_request(0);
    ASSERT_EQ(0, _upper->getNumReplies());
}

TEST_F(StateManagerTest, immediate_node_state_replying_is_tracked_per_controller) {
    mark_reported_node_state_up();
    mark_reply_observed_from_n_controllers(3);

    _manager->immediately_send_get_node_state_replies();

    send_down_get_node_state_request(0);
    send_down_get_node_state_request(1);
    send_down_get_node_state_request(2);
    ASSERT_EQ(3, _upper->getNumReplies());
    clear_sent_replies();

    // Sending a new request should now _not_ immediately receive a reply
    send_down_get_node_state_request(0);
    send_down_get_node_state_request(1);
    send_down_get_node_state_request(2);
    ASSERT_EQ(0, _upper->getNumReplies());
}

TEST_F(StateManagerTest, request_almost_immediate_replies_triggers_fast_reply)
{
    mark_reported_node_state_up();
    mark_reply_observed_from_n_controllers(1);
    auto before = std::chrono::steady_clock::now();
    for (size_t pass = 0; pass < 100; ++pass) {
        send_down_get_node_state_request(0);
        _manager->request_almost_immediate_node_state_replies();
        _upper->waitForMessage(api::MessageType::GETNODESTATE_REPLY, 2);
        clear_sent_replies();
    }
    auto after = std::chrono::steady_clock::now();
    ASSERT_GT(10s, after - before);
}

TEST_F(StateManagerTest, activation_command_is_bounced_with_current_cluster_state_version) {
    force_current_cluster_state_version(12345);

    auto cmd = std::make_shared<api::ActivateClusterStateVersionCommand>(12340);
    cmd->setTimeout(10000000ms);
    cmd->setSourceIndex(0);
    _upper->sendDown(cmd);

    ASSERT_EQ(1, _upper->getNumReplies());
    std::shared_ptr<api::StorageReply> reply;
    GET_ONLY_OK_REPLY(reply); // Implicitly clears messages from _upper
    ASSERT_EQ(api::MessageType::ACTIVATE_CLUSTER_STATE_VERSION_REPLY, reply->getType());
    auto& activate_reply = dynamic_cast<api::ActivateClusterStateVersionReply&>(*reply);
    EXPECT_EQ(12340, activate_reply.activateVersion());
    EXPECT_EQ(12345, activate_reply.actualVersion());
}

} // storage

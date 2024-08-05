// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/dummystoragelink.h>
#include <tests/common/storage_config_set.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/storage/storageserver/statemanager.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/vespalib/gtest/gtest.h>

using storage::lib::NodeState;
using storage::lib::NodeType;
using storage::lib::State;
using storage::lib::ClusterState;
using namespace ::testing;

namespace storage {

struct StateManagerTest : Test, NodeStateReporter {
    std::unique_ptr<StorageConfigSet> _config;
    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<DummyStorageLink> _upper;
    StateManager* _manager;
    DummyStorageLink* _lower;

    StateManagerTest();

    void SetUp() override;
    void TearDown() override;

    static std::shared_ptr<api::SetSystemStateCommand> make_set_state_cmd(std::string_view state_str, uint16_t cc_index) {
        auto cmd = std::make_shared<api::SetSystemStateCommand>(lib::ClusterState(state_str));
        cmd->setSourceIndex(cc_index);
        return cmd;
    }

    static std::shared_ptr<const lib::ClusterStateBundle> make_state_bundle_with_config(
            std::string_view state_str, uint16_t num_nodes)
    {
        auto state = std::make_shared<const ClusterState>(state_str);
        auto distr = lib::DistributionConfigBundle::of(lib::Distribution::getDefaultDistributionConfig(1, num_nodes));
        return std::make_shared<lib::ClusterStateBundle>(std::move(state),
                                                         lib::ClusterStateBundle::BucketSpaceStateMapping{},
                                                         std::nullopt, std::move(distr), false);
    }


    static std::shared_ptr<api::SetSystemStateCommand> make_set_state_cmd_with_config(
            std::string_view state_str, uint16_t num_nodes)
    {
        return std::make_shared<api::SetSystemStateCommand>(make_state_bundle_with_config(state_str, num_nodes));
    }

    void get_single_reply(std::shared_ptr<api::StorageReply>& reply_out);
    void get_only_ok_reply(std::shared_ptr<api::StorageReply>& reply_out);
    void force_current_cluster_state_version(uint32_t version, uint16_t cc_index);
    void force_current_cluster_state_version(uint32_t version) {
        force_current_cluster_state_version(version, 0);
    }
    void mark_reported_node_state_up();
    void send_down_get_node_state_request(uint16_t controller_index);
    void assert_ok_get_node_state_reply_sent_and_clear();
    void clear_sent_replies();
    void mark_reply_observed_from_n_controllers(uint16_t n);

    std::string get_node_info() const {
        return _manager->getNodeInfo();
    }
    void report(vespalib::JsonStream &) const override {}

    void extract_cluster_state_version_from_host_info(uint32_t& version_out);

    static vespalib::string to_string(const lib::Distribution::DistributionConfig& cfg) {
        return lib::Distribution(cfg).serialized();
    }
};

StateManagerTest::StateManagerTest()
    : _config(),
      _node(),
      _upper(),
      _manager(nullptr),
      _lower(nullptr)
{
}

void
StateManagerTest::SetUp()
{
    _config = StorageConfigSet::make_storage_node_config();
    _node = std::make_unique<TestServiceLayerApp>(NodeIndex(2), _config->config_uri());
    // Clock will increase 1 sec per call.
    _node->getClock().setAbsoluteTimeInSeconds(1);
    _upper = std::make_unique<DummyStorageLink>();
    _manager = new StateManager(_node->getComponentRegister(), std::make_unique<HostInfo>(), *this, false);
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
}

void
StateManagerTest::get_single_reply(std::shared_ptr<api::StorageReply>& reply_out)
{
    ASSERT_EQ(_upper->getNumReplies(), 1);
    ASSERT_TRUE(_upper->getReply(0)->getType().isReply());
    reply_out = std::dynamic_pointer_cast<api::StorageReply>(_upper->getReply(0));
    ASSERT_TRUE(reply_out.get() != nullptr);
    _upper->reset();
}

void
StateManagerTest::get_only_ok_reply(std::shared_ptr<api::StorageReply>& reply_out)
{
    ASSERT_NO_FATAL_FAILURE(get_single_reply(reply_out));
    ASSERT_EQ(reply_out->getResult(), api::ReturnCode(api::ReturnCode::OK));
}

void
StateManagerTest::force_current_cluster_state_version(uint32_t version, uint16_t cc_index)
{
    ClusterState state(*_manager->getClusterStateBundle()->getBaselineClusterState());
    state.setVersion(version);
    const auto maybe_rejected_by_ver = _manager->try_set_cluster_state_bundle(
            std::make_shared<const lib::ClusterStateBundle>(state), cc_index);
    ASSERT_EQ(maybe_rejected_by_ver, std::nullopt);
}

void
StateManagerTest::extract_cluster_state_version_from_host_info(uint32_t& version_out)
{
    std::string nodeInfoString = get_node_info();
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

    version_out = clusterStateVersionCursor.asLong();
}

TEST_F(StateManagerTest, cluster_state_and_config_has_expected_values_at_bootstrap) {
    auto initial_bundle = _manager->getClusterStateBundle();
    auto currentState = initial_bundle->getBaselineClusterState();
    EXPECT_EQ("cluster:d", currentState->toString(false));
    EXPECT_EQ(currentState->getVersion(), 0);

    // Distribution config should be equal to the config the node is running with.
    ASSERT_TRUE(initial_bundle->has_distribution_config());
    EXPECT_EQ(to_string(initial_bundle->distribution_config_bundle()->config()),
              _node->getComponentRegister().getDistribution()->serialized());

    auto currentNodeState = _manager->getCurrentNodeState();
    EXPECT_EQ("s:d", currentNodeState->toString(false));
}

TEST_F(StateManagerTest, can_receive_state_bundle_without_distribution_config) {
    ClusterState send_state("version:2 distributor:1 storage:4 .2.s:m");
    auto cmd = std::make_shared<api::SetSystemStateCommand>(send_state);
    _upper->sendDown(cmd);
    std::shared_ptr<api::StorageReply> reply;
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));

    auto current_bundle = _manager->getClusterStateBundle();
    EXPECT_EQ(send_state, *current_bundle->getBaselineClusterState());
    // Distribution config should be unchanged from bootstrap.
    ASSERT_TRUE(current_bundle->has_distribution_config());
    EXPECT_EQ(to_string(current_bundle->distribution_config_bundle()->config()),
              _node->getComponentRegister().getDistribution()->serialized());

    auto current_node_state = _manager->getCurrentNodeState();
    EXPECT_EQ("s:m", current_node_state->toString(false));
}

TEST_F(StateManagerTest, can_receive_state_bundle_with_distribution_config) {
    auto cmd = make_set_state_cmd_with_config("version:2 distributor:1 storage:4 .2.s:m", 5);
    EXPECT_NE(to_string(cmd->getClusterStateBundle().distribution_config_bundle()->config()),
              _node->getComponentRegister().getDistribution()->serialized());
    _upper->sendDown(cmd);
    std::shared_ptr<api::StorageReply> reply;
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));

    auto current_bundle = _manager->getClusterStateBundle();
    EXPECT_EQ(*current_bundle, cmd->getClusterStateBundle()); // also compares distribution configs
}

TEST_F(StateManagerTest, receiving_cc_bundle_with_distribution_config_disables_node_distribution_config_propagation) {
    auto cmd = make_set_state_cmd_with_config("version:2 distributor:1 storage:4 .2.s:m", 5);
    _upper->sendDown(cmd);
    std::shared_ptr<api::StorageReply> reply;
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));
    // Explicitly setting distribution config should not propagate to the active state bundle
    // since we've flipped to expecting config from the cluster controllers instead.
    auto distr = std::make_shared<lib::Distribution>(lib::Distribution::getDefaultDistributionConfig(2, 7));
    _node->getComponentRegister().setDistribution(distr);

    auto current_bundle = _manager->getClusterStateBundle();
    EXPECT_EQ(*current_bundle, cmd->getClusterStateBundle()); // unchanged
}

TEST_F(StateManagerTest, internal_distribution_config_is_propagated_if_none_yet_received_from_cc) {
    _upper->sendDown(make_set_state_cmd("version:10 distributor:1 storage:4", 0));
    std::shared_ptr<api::StorageReply> reply;
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));

    auto expected_bundle = make_state_bundle_with_config("version:10 distributor:1 storage:4", 7);
    // Explicitly set internal config
    _node->getComponentRegister().setDistribution(expected_bundle->distribution_config_bundle()->default_distribution_sp());
    _manager->storageDistributionChanged();

    auto current_bundle = _manager->getClusterStateBundle();
    EXPECT_EQ(*current_bundle, *expected_bundle);
}

TEST_F(StateManagerTest, revert_to_internal_config_if_cc_no_longer_sends_distribution_config) {
    // Initial state bundle _with_ distribution config
    auto cmd = make_set_state_cmd_with_config("version:2 distributor:1 storage:4 .2.s:m", 5);
    _upper->sendDown(cmd);
    std::shared_ptr<api::StorageReply> reply;
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));

    auto current_bundle = _manager->getClusterStateBundle();
    EXPECT_EQ(to_string(current_bundle->distribution_config_bundle()->config()),
              to_string(cmd->getClusterStateBundle().distribution_config_bundle()->config()));

    // CC then sends a new bundle _without_ config
    _upper->sendDown(make_set_state_cmd("version:3 distributor:1 storage:4", 0));
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));

    // Config implicitly reverted to the active internal config
    current_bundle = _manager->getClusterStateBundle();
    EXPECT_EQ(to_string(current_bundle->distribution_config_bundle()->config()),
              _node->getComponentRegister().getDistribution()->serialized());

    // Explicitly set internal config
    auto expected_bundle = make_state_bundle_with_config("version:3 distributor:1 storage:4", 7);
    _node->getComponentRegister().setDistribution(expected_bundle->distribution_config_bundle()->default_distribution_sp());
    _manager->storageDistributionChanged();

    // Internal config shall have taken effect, overriding that of the initial bundle
    current_bundle = _manager->getClusterStateBundle();
    EXPECT_EQ(*current_bundle, *expected_bundle);
}

TEST_F(StateManagerTest, accept_lower_state_versions_if_strict_requirement_disabled) {
    _manager->set_require_strictly_increasing_cluster_state_versions(false);

    ASSERT_NO_FATAL_FAILURE(force_current_cluster_state_version(123, 1)); // CC 1
    ASSERT_EQ(_manager->getClusterStateBundle()->getVersion(), 123);

    _upper->sendDown(make_set_state_cmd("version:122 distributor:1 storage:1", 0)); // CC 0
    std::shared_ptr<api::StorageReply> reply;
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));
    EXPECT_EQ(_manager->getClusterStateBundle()->getVersion(), 122);
}

TEST_F(StateManagerTest, reject_lower_state_versions_if_strict_requirement_enabled) {
    _manager->set_require_strictly_increasing_cluster_state_versions(true);

    ASSERT_NO_FATAL_FAILURE(force_current_cluster_state_version(123, 1)); // CC 1
    ASSERT_EQ(_manager->getClusterStateBundle()->getVersion(), 123);

    _upper->sendDown(make_set_state_cmd("version:122 distributor:1 storage:1", 0)); // CC 0
    std::shared_ptr<api::StorageReply> reply;
    ASSERT_NO_FATAL_FAILURE(get_single_reply(reply));
    api::ReturnCode expected_res(api::ReturnCode::REJECTED, "Cluster state version 122 rejected; node already has "
                                                            "a higher cluster state version (123)");
    EXPECT_EQ(reply->getResult(), expected_res);
    EXPECT_EQ(_manager->getClusterStateBundle()->getVersion(), 123);
}

// Observing a lower cluster state version from the same CC index directly implies that the ZooKeeper
// state has been lost, at which point we pragmatically (but begrudgingly) accept the state version
// to avoid stalling the entire cluster for an indeterminate amount of time.
TEST_F(StateManagerTest, accept_lower_state_versions_from_same_cc_index_even_if_strict_requirement_enabled) {
    _manager->set_require_strictly_increasing_cluster_state_versions(true);

    ASSERT_NO_FATAL_FAILURE(force_current_cluster_state_version(123, 1)); // CC 1
    ASSERT_EQ(_manager->getClusterStateBundle()->getVersion(), 123);

    ASSERT_NO_FATAL_FAILURE(force_current_cluster_state_version(124, 2)); // CC 2
    ASSERT_EQ(_manager->getClusterStateBundle()->getVersion(), 124);

    // CC 1 restarts from scratch with previous ZK state up in smoke.
    _upper->sendDown(make_set_state_cmd("version:3 distributor:1 storage:1", 1));
    std::shared_ptr<api::StorageReply> reply;
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));
    EXPECT_EQ(_manager->getClusterStateBundle()->getVersion(), 3);

    // CC 2 restarts and continues from where CC 1 left off.
    _upper->sendDown(make_set_state_cmd("version:4 distributor:1 storage:1", 2));
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));
    EXPECT_EQ(_manager->getClusterStateBundle()->getVersion(), 4);
}

namespace {
struct MyStateListener : public StateListener {
    const NodeStateUpdater& updater;
    lib::NodeState current;
    std::ostringstream ost;

    explicit MyStateListener(const NodeStateUpdater& upd);
    ~MyStateListener() override;

    void handleNewState() noexcept override {
        ost << current << " -> ";
        current = *updater.getReportedNodeState();
        ost << current << "\n";
    }
};

MyStateListener::MyStateListener(const NodeStateUpdater& upd)
    : updater(upd), current(*updater.getReportedNodeState())
{}
MyStateListener::~MyStateListener() = default;
}

TEST_F(StateManagerTest, reported_node_state) {
    std::shared_ptr<api::StorageReply> reply;
    // Add a state listener to check that we get events.
    MyStateListener stateListener(*_manager);
    _manager->addStateListener(stateListener);
    // Test that initial state is Down
    auto nodeState = _manager->getReportedNodeState();
    EXPECT_EQ("s:d b:58 t:1", nodeState->toString(false));
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
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));
    ASSERT_EQ(api::MessageType::GETNODESTATE_REPLY, reply->getType());
    nodeState = std::make_shared<NodeState>(
                dynamic_cast<api::GetNodeStateReply&>(*reply).getNodeState());
    EXPECT_EQ("s:u b:58 t:1", nodeState->toString(false));
    // We should also get it with wrong expected state
    cmd = std::make_shared<api::GetNodeStateCommand>(
            std::make_unique<NodeState>(NodeType::STORAGE, State::INITIALIZING));
    _upper->sendDown(cmd);
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));
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

    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));
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
            "s:d b:58 t:1 -> s:u b:58 t:1\n"
            "s:u b:58 t:1 -> s:s b:58 t:1 m:Stopping\\x20node\n";
    EXPECT_EQ(expectedEvents, stateListener.ost.str());
}

TEST_F(StateManagerTest, current_cluster_state_version_is_included_in_host_info_json) {
    ASSERT_NO_FATAL_FAILURE(force_current_cluster_state_version(123));
    uint32_t version;
    ASSERT_NO_FATAL_FAILURE(extract_cluster_state_version_from_host_info(version));
    EXPECT_EQ(version, 123);
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
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply)); // Implicitly clears messages from _upper
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
    ASSERT_NO_FATAL_FAILURE(force_current_cluster_state_version(12345));

    auto cmd = std::make_shared<api::ActivateClusterStateVersionCommand>(12340);
    cmd->setTimeout(10000000ms);
    cmd->setSourceIndex(0);
    _upper->sendDown(cmd);

    ASSERT_EQ(1, _upper->getNumReplies());
    std::shared_ptr<api::StorageReply> reply;
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply)); // Implicitly clears messages from _upper
    ASSERT_EQ(api::MessageType::ACTIVATE_CLUSTER_STATE_VERSION_REPLY, reply->getType());
    auto& activate_reply = dynamic_cast<api::ActivateClusterStateVersionReply&>(*reply);
    EXPECT_EQ(12340, activate_reply.activateVersion());
    EXPECT_EQ(12345, activate_reply.actualVersion());
}

TEST_F(StateManagerTest, non_deferred_cluster_state_sets_reported_cluster_state_version) {
    auto cmd = std::make_shared<api::SetSystemStateCommand>(lib::ClusterState("version:1234 distributor:1 storage:1"));
    cmd->setTimeout(1000s);
    cmd->setSourceIndex(0);
    _upper->sendDown(cmd);
    std::shared_ptr<api::StorageReply> reply;
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));

    uint32_t version;
    ASSERT_NO_FATAL_FAILURE(extract_cluster_state_version_from_host_info(version));
    EXPECT_EQ(version, 1234);
}

TEST_F(StateManagerTest, deferred_cluster_state_does_not_update_state_until_activation_edge) {
    ASSERT_NO_FATAL_FAILURE(force_current_cluster_state_version(100));

    lib::ClusterStateBundle deferred_bundle(lib::ClusterState("version:101 distributor:1 storage:1"), {}, true);
    auto state_cmd = std::make_shared<api::SetSystemStateCommand>(deferred_bundle);
    state_cmd->setTimeout(1000s);
    state_cmd->setSourceIndex(0);
    _upper->sendDown(state_cmd);
    std::shared_ptr<api::StorageReply> reply;
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));

    uint32_t version;
    ASSERT_NO_FATAL_FAILURE(extract_cluster_state_version_from_host_info(version));
    EXPECT_EQ(version, 100); // Not yet updated to version 101

    auto activation_cmd = std::make_shared<api::ActivateClusterStateVersionCommand>(101);
    activation_cmd->setTimeout(1000s);
    activation_cmd->setSourceIndex(0);
    _upper->sendDown(activation_cmd);
    ASSERT_NO_FATAL_FAILURE(get_only_ok_reply(reply));

    ASSERT_NO_FATAL_FAILURE(extract_cluster_state_version_from_host_info(version));
    EXPECT_EQ(version, 101);
}

} // storage

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/common/nodestateupdater.h>
#include <mutex>

namespace storage::lib {
    class ClusterState;
    class ClusterStateBundle;
    class Distribution;
}
namespace storage {

struct TestNodeStateUpdater : public NodeStateUpdater
{
    mutable std::mutex  _mutex;
    lib::NodeState::CSP _reported;
    lib::NodeState::CSP _current;
    std::shared_ptr<const lib::ClusterStateBundle> _clusterStateBundle;
    std::vector<StateListener*> _listeners;
    size_t _explicit_node_state_reply_send_invocations;
    size_t _requested_almost_immediate_node_state_replies;

public:
    explicit TestNodeStateUpdater(const lib::NodeType& type);
    ~TestNodeStateUpdater() override;

    lib::NodeState::CSP getReportedNodeState() const override { std::lock_guard guard(_mutex); return _reported; }
    lib::NodeState::CSP getCurrentNodeState() const override { return _current; }
    std::shared_ptr<const lib::ClusterStateBundle> getClusterStateBundle() const override;
    void addStateListener(StateListener& s) override { _listeners.push_back(&s); }
    void removeStateListener(StateListener&) override {}
    Lock::SP grabStateChangeLock() override { return std::make_shared<Lock>(); }
    void setReportedNodeState(const lib::NodeState& state) override {
        std::lock_guard guard(_mutex);
        _reported = std::make_shared<lib::NodeState>(state);
    }
    void immediately_send_get_node_state_replies() override {
        ++_explicit_node_state_reply_send_invocations;
    }

    void request_almost_immediate_node_state_replies() override {
        ++_requested_almost_immediate_node_state_replies;
    }

    void setCurrentNodeState(const lib::NodeState& state) {
        _current = std::make_shared<lib::NodeState>(state);
    }

    void patch_distribution(std::shared_ptr<const lib::Distribution> distribution);
    void setClusterState(std::shared_ptr<const lib::ClusterState> c);
    void setClusterStateBundle(std::shared_ptr<const lib::ClusterStateBundle> clusterStateBundle);

    size_t explicit_node_state_reply_send_invocations() const noexcept {
        return _explicit_node_state_reply_send_invocations;
    }

    size_t requested_almost_immediate_node_state_replies() const noexcept {
        return _requested_almost_immediate_node_state_replies;
    }
};

} // storage

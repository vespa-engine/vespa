// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::TestNodeStateUpdater
 * \ingroup common
 *
 * \brief Test implementation of the node state updater.
 */

#pragma once

#include <vespa/storage/common/nodestateupdater.h>

namespace storage {

struct TestNodeStateUpdater : public NodeStateUpdater
{
    lib::NodeState::CSP _reported;
    lib::NodeState::CSP _current;
    std::shared_ptr<const lib::ClusterStateBundle> _clusterStateBundle;
    std::vector<StateListener*> _listeners;

public:
    explicit TestNodeStateUpdater(const lib::NodeType& type);
    ~TestNodeStateUpdater() override;

    lib::NodeState::CSP getReportedNodeState() const override { return _reported; }
    lib::NodeState::CSP getCurrentNodeState() const override { return _current; }
    std::shared_ptr<const lib::ClusterStateBundle> getClusterStateBundle() const override;
    void addStateListener(StateListener& s) override { _listeners.push_back(&s); }
    void removeStateListener(StateListener&) override {}
    Lock::SP grabStateChangeLock() override { return std::make_shared<Lock>(); }
    void setReportedNodeState(const lib::NodeState& state) override {
        _reported = std::make_shared<lib::NodeState>(state);
    }
    void immediately_send_get_node_state_replies() override {}

    void setCurrentNodeState(const lib::NodeState& state) {
        _current = std::make_shared<lib::NodeState>(state);
    }

    void setClusterState(lib::ClusterState::CSP c);
};

} // storage

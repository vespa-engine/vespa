// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    lib::ClusterState::CSP _cluster;
    std::vector<StateListener*> _listeners;

public:
    TestNodeStateUpdater(const lib::NodeType& type) {
        _reported.reset(new lib::NodeState(type, lib::State::UP));
        _current.reset(new lib::NodeState(type, lib::State::UP));
    }

    lib::NodeState::CSP getReportedNodeState() const { return _reported; }
    lib::NodeState::CSP getCurrentNodeState() const { return _current; }
    lib::ClusterState::CSP getSystemState() const { return _cluster; }
    void addStateListener(StateListener& s) {
        _listeners.push_back(&s);
    }
    void removeStateListener(StateListener&) {}
    Lock::SP grabStateChangeLock() { return Lock::SP(new Lock); }
    void setReportedNodeState(const lib::NodeState& state)
        { _reported.reset(new lib::NodeState(state)); }
    void setCurrentNodeState(const lib::NodeState& state)
        { _current.reset(new lib::NodeState(state)); }

    void setClusterState(lib::ClusterState::CSP c) {
        _cluster = c;
        for (uint32_t i = 0; i < _listeners.size(); ++i) {
            _listeners[i]->handleNewState();
        }
    }
};

} // storage


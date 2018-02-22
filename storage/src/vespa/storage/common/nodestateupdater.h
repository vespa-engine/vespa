// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::NodeStateUpdater
 * @ingroup common
 *
 * @brief Interface to implement for handler of state updates
 *
 * This component is responsible for keeping the node and system state, and
 * make it available to all components that want to access it. For thread
 * safety it returns shared pointers to states, such that state objects
 * retrieved are still valid after changes.
 *
 * If you're using the state so much that copying the shared pointer is too
 * much, you can instead add yourself as a state listener, and keep your own
 * copy of the state.
 *
 * When you set a new reported state, pending get node state requests will be
 * answered, so do all your updates in one call.
 *
 * This interface exist so the storage server interface is not implementation
 * dependent, and such that the state updater can be easily faked in tests.
 *
 */
#pragma once

#include <string>
#include <vespa/vdslib/state/nodestate.h>
#include <vespa/vdslib/state/clusterstate.h>

namespace storage {

namespace lib { class ClusterStateBundle; }

struct StateListener {
    virtual ~StateListener() {}
    virtual void handleNewState() = 0;
};

struct NodeStateUpdater {
    typedef std::unique_ptr<NodeStateUpdater> UP;

    virtual ~NodeStateUpdater() {}

    virtual lib::NodeState::CSP getReportedNodeState() const = 0;
    virtual lib::NodeState::CSP getCurrentNodeState() const = 0;
    virtual std::shared_ptr<const lib::ClusterStateBundle> getClusterStateBundle() const = 0;

    virtual void addStateListener(StateListener&) = 0;
    virtual void removeStateListener(StateListener&) = 0;

    /**
     * Multiple components typically request state, changes something and sets
     * it back. To prevent race conditions here, they should grab this lock
     * before altering the state.
     */
    struct Lock {
        typedef std::shared_ptr<Lock> SP;
        virtual ~Lock() {}
    };
    virtual Lock::SP grabStateChangeLock() = 0;

    /**
     * Sets the node state. Remember that other components might be setting
     * parts of the node state you don't care about. Thus, when you alter the
     * nodestate, first retrieve it and only change the parts you want to.
     */
    virtual void setReportedNodeState(const lib::NodeState& state) = 0;
};

} // storage



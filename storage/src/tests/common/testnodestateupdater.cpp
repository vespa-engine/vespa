// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testnodestateupdater.h"
#include <vespa/vdslib/state/cluster_state_bundle.h>

namespace storage {

TestNodeStateUpdater::TestNodeStateUpdater(const lib::NodeType& type)
    : _reported(new lib::NodeState(type, lib::State::UP)),
      _current(new lib::NodeState(type, lib::State::UP)),
      _clusterStateBundle(std::make_shared<const lib::ClusterStateBundle>(lib::ClusterState())),
      _listeners(),
      _explicit_node_state_reply_send_invocations(0)
{ }

TestNodeStateUpdater::~TestNodeStateUpdater() = default;

std::shared_ptr<const lib::ClusterStateBundle>
TestNodeStateUpdater::getClusterStateBundle() const
{
    return _clusterStateBundle;
}

void
TestNodeStateUpdater::setClusterState(lib::ClusterState::CSP c)
{
    setClusterStateBundle(std::make_shared<const lib::ClusterStateBundle>(*c));
}

void
TestNodeStateUpdater::setClusterStateBundle(std::shared_ptr<const lib::ClusterStateBundle> clusterStateBundle)
{
    _clusterStateBundle = std::move(clusterStateBundle);
    for (uint32_t i = 0; i < _listeners.size(); ++i) {
        _listeners[i]->handleNewState();
    }
}

}

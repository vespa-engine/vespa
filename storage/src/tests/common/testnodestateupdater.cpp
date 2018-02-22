// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testnodestateupdater.h"
#include <vespa/storage/common/cluster_state_bundle.h>

namespace storage {

TestNodeStateUpdater::TestNodeStateUpdater(const lib::NodeType& type)
    : _reported(new lib::NodeState(type, lib::State::UP)),
      _current(new lib::NodeState(type, lib::State::UP)),
      _clusterStateBundle(),
      _listeners()
{ }

TestNodeStateUpdater::~TestNodeStateUpdater() = default;

std::shared_ptr<const ClusterStateBundle>
TestNodeStateUpdater::getClusterStateBundle() const
{
    return _clusterStateBundle;
}

void
TestNodeStateUpdater::setClusterState(lib::ClusterState::CSP c)
{
    _clusterStateBundle = std::make_shared<const ClusterStateBundle>(*c);
    for (uint32_t i = 0; i < _listeners.size(); ++i) {
        _listeners[i]->handleNewState();
    }
}

}

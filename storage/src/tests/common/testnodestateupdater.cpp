// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testnodestateupdater.h"
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>

namespace storage {

TestNodeStateUpdater::TestNodeStateUpdater(const lib::NodeType& type)
    : _mutex(),
      _reported(new lib::NodeState(type, lib::State::UP)),
      _current(new lib::NodeState(type, lib::State::UP)),
      _clusterStateBundle(std::make_shared<const lib::ClusterStateBundle>(lib::ClusterState())),
      _listeners(),
      _explicit_node_state_reply_send_invocations(0),
      _requested_almost_immediate_node_state_replies(0)
{ }

TestNodeStateUpdater::~TestNodeStateUpdater() = default;

std::shared_ptr<const lib::ClusterStateBundle>
TestNodeStateUpdater::getClusterStateBundle() const
{
    return _clusterStateBundle;
}

void
TestNodeStateUpdater::patch_distribution(std::shared_ptr<const lib::Distribution> distribution)
{
    _clusterStateBundle = _clusterStateBundle->clone_with_new_distribution(
            lib::DistributionConfigBundle::of(std::move(distribution)));
}

void
TestNodeStateUpdater::setClusterState(std::shared_ptr<const lib::ClusterState> c)
{
    setClusterStateBundle(std::make_shared<const lib::ClusterStateBundle>(std::move(c)));
}

void
TestNodeStateUpdater::setClusterStateBundle(std::shared_ptr<const lib::ClusterStateBundle> clusterStateBundle)
{
    auto existing_distr = _clusterStateBundle->distribution_config_bundle();
    _clusterStateBundle = std::move(clusterStateBundle);
    if (!_clusterStateBundle->has_distribution_config() && existing_distr) {
        _clusterStateBundle = _clusterStateBundle->clone_with_new_distribution(existing_distr);
    }
    for (auto* listener : _listeners) {
        listener->handleNewState();
    }
}

}

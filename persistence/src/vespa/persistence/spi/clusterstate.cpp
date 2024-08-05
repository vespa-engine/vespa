// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "clusterstate.h"
#include "bucket.h"
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>

using vespalib::Trinary;

namespace storage::spi {

ClusterState::ClusterState(std::shared_ptr<const lib::ClusterState> state,
                           std::shared_ptr<const lib::Distribution> distribution,
                           uint16_t node_index,
                           bool maintenance_in_all_spaces)
    : _state(std::move(state)),
      _distribution(std::move(distribution)),
      _nodeIndex(node_index),
      _maintenanceInAllSpaces(maintenance_in_all_spaces)
{
}

ClusterState::ClusterState(const lib::ClusterState& state,
                           uint16_t nodeIndex,
                           const lib::Distribution& distribution,
                           bool maintenanceInAllSpaces)
    : _state(std::make_shared<const lib::ClusterState>(state)),
      _distribution(std::make_shared<const lib::Distribution>(distribution.serialized())),
      _nodeIndex(nodeIndex),
      _maintenanceInAllSpaces(maintenanceInAllSpaces)
{
}

ClusterState::ClusterState(const ClusterState& other) = default;

ClusterState::~ClusterState() = default;

Trinary
ClusterState::shouldBeReady(const Bucket& b) const {
    assert(_distribution);
    assert(_state);

    if (b.getBucketId().getUsedBits() < _state->getDistributionBitCount()) {
        return Trinary::Undefined;
    }

    if (_distribution->getReadyCopies() >= _distribution->getRedundancy()) {
        return Trinary::True; // all copies should be ready
    }

    std::vector<uint16_t> idealNodes;
    _distribution->getIdealNodes(lib::NodeType::STORAGE, *_state,
                                 b.getBucketId(), idealNodes,
                                 "uim", _distribution->getReadyCopies());
    for (uint16_t node : idealNodes) {
        if (node == _nodeIndex) return Trinary::True;
    }
    return Trinary::False;
}

bool ClusterState::clusterUp() const noexcept {
    return _state && _state->getClusterState() == lib::State::UP;
}

bool ClusterState::nodeHasStateOneOf(const char* states) const noexcept {
    return _state &&
           _state->getNodeState(lib::Node(lib::NodeType::STORAGE, _nodeIndex)).
                   getState().oneOf(states);
}

bool ClusterState::nodeUp() const noexcept {
    return nodeHasStateOneOf("uir");
}

bool ClusterState::nodeInitializing() const noexcept {
    return nodeHasStateOneOf("i");
}

bool ClusterState::nodeRetired() const noexcept {
    return nodeHasStateOneOf("r");
}

bool ClusterState::nodeMaintenance() const noexcept {
    return _maintenanceInAllSpaces;
}

}

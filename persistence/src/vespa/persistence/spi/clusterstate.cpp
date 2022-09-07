// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "clusterstate.h"
#include "bucket.h"
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>

using vespalib::Trinary;

namespace storage::spi {

ClusterState::ClusterState(const lib::ClusterState& state,
                           uint16_t nodeIndex,
                           const lib::Distribution& distribution,
                           bool maintenanceInAllSpaces)
    : _state(std::make_unique<lib::ClusterState>(state)),
      _distribution(std::make_unique<lib::Distribution>(distribution.serialize())),
      _nodeIndex(nodeIndex),
      _maintenanceInAllSpaces(maintenanceInAllSpaces)
{
}

void ClusterState::deserialize(vespalib::nbostream& i) {
    vespalib::string clusterState;
    vespalib::string distribution;

    i >> clusterState;
    i >> _nodeIndex;
    i >> distribution;

    _state = std::make_unique<lib::ClusterState>(clusterState);
    _distribution = std::make_unique<lib::Distribution>(distribution);
}

ClusterState::ClusterState(const ClusterState& other) {
    vespalib::nbostream o;
    other.serialize(o);
    deserialize(o);
    _maintenanceInAllSpaces = other._maintenanceInAllSpaces;
}

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

void ClusterState::serialize(vespalib::nbostream& o) const {
    assert(_distribution);
    assert(_state);
    vespalib::asciistream tmp;
    _state->serialize(tmp, false);
    o << tmp.str() << _nodeIndex;
    o << _distribution->serialize();
}

}

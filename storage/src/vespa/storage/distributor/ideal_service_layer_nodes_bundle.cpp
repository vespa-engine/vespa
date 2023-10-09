// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ideal_service_layer_nodes_bundle.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace storage::distributor {

namespace {
constexpr size_t BUILD_HASH_LIMIT = 32;
}

struct IdealServiceLayerNodesBundle::LookupMap : public vespalib::hash_map<uint16_t, Index> {
    using Parent = vespalib::hash_map<uint16_t, Index>;
    using Parent::Parent;
};

IdealServiceLayerNodesBundle::IdealServiceLayerNodesBundle() noexcept = default;
IdealServiceLayerNodesBundle::IdealServiceLayerNodesBundle(IdealServiceLayerNodesBundle &&) noexcept = default;
IdealServiceLayerNodesBundle::~IdealServiceLayerNodesBundle() = default;

void
IdealServiceLayerNodesBundle::set_nodes(ConstNodesRef nodes,
                                        ConstNodesRef nonretired_nodes,
                                        ConstNodesRef nonretired_or_maintenance_nodes)
{
    _nodes.clear();
    _nodes.reserve(nodes.size() + nonretired_nodes.size() + nonretired_or_maintenance_nodes.size());
    std::for_each(nodes.cbegin(), nodes.cend(), [this](uint16_t n) { _nodes.emplace_back(n); });
    _available_sz = nodes.size();
    std::for_each(nonretired_nodes.cbegin(), nonretired_nodes.cend(), [this](uint16_t n) { _nodes.emplace_back(n); });
    _nonretired_sz = nonretired_nodes.size();
    std::for_each(nonretired_or_maintenance_nodes.cbegin(), nonretired_or_maintenance_nodes.cend(), [this](uint16_t n) { _nodes.emplace_back(n); });

    if (nonretired_or_maintenance_nodes.size() > BUILD_HASH_LIMIT) {
        _nonretired_or_maintenance_node_2_index = std::make_unique<LookupMap>(nonretired_or_maintenance_nodes.size());
        for (uint16_t i(0); i < nonretired_or_maintenance_nodes.size(); i++) {
            _nonretired_or_maintenance_node_2_index->insert(std::make_pair(nonretired_or_maintenance_nodes[i], Index(i)));
        }
    }
}

IdealServiceLayerNodesBundle::Index
IdealServiceLayerNodesBundle::ConstNodesRef2Index::lookup(uint16_t node) const noexcept {
    for (uint16_t i(0); i < _idealState.size(); i++) {
        if (node == _idealState[i]) return Index(i);
    }
    return Index::invalid();
}

IdealServiceLayerNodesBundle::Index
IdealServiceLayerNodesBundle::nonretired_or_maintenance_index(uint16_t node) const noexcept {
    if (_nonretired_or_maintenance_node_2_index) {
        const auto found = _nonretired_or_maintenance_node_2_index->find(node);
        return (found != _nonretired_or_maintenance_node_2_index->end()) ? found->second : Index::invalid();
    } else {
        return ConstNodesRef2Index(available_nonretired_or_maintenance_nodes()).lookup(node);
    }
}

}

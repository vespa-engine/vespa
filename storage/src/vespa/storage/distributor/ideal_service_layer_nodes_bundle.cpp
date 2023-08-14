// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ideal_service_layer_nodes_bundle.h"
#include <vespa/vdslib/distribution/idealnodecalculator.h>
#include <vespa/vespalib/stllike/hash_set_insert.hpp>


namespace storage::distributor {

IdealServiceLayerNodesBundle::IdealServiceLayerNodesBundle() noexcept
  : _available_nodes(),
    _available_nonretired_nodes(),
    _available_nonretired_or_maintenance_nodes(),
    _unordered_nonretired_or_maintenance_nodes()
{
}

void
IdealServiceLayerNodesBundle::set_available_nonretired_or_maintenance_nodes(std::vector<uint16_t> available_nonretired_or_maintenance_nodes) {
    _available_nonretired_or_maintenance_nodes = std::move(available_nonretired_or_maintenance_nodes);
    _unordered_nonretired_or_maintenance_nodes.clear();
    _unordered_nonretired_or_maintenance_nodes.insert(_available_nonretired_or_maintenance_nodes.begin(),
                                                      _available_nonretired_or_maintenance_nodes.end());
}

IdealServiceLayerNodesBundle::IdealServiceLayerNodesBundle(IdealServiceLayerNodesBundle &&) noexcept = default;

IdealServiceLayerNodesBundle::~IdealServiceLayerNodesBundle() = default;

}

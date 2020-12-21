// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ideal_service_layer_nodes_bundle.h"
#include <vespa/vdslib/distribution/idealnodecalculator.h>

namespace storage::distributor {

IdealServiceLayerNodesBundle::IdealServiceLayerNodesBundle() noexcept
  : _available_nodes(),
    _available_nonretired_nodes(),
    _available_nonretired_or_maintenance_nodes()
{
}

IdealServiceLayerNodesBundle::~IdealServiceLayerNodesBundle() = default;

}

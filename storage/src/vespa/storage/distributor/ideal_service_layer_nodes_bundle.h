// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <cstdint>

namespace storage::distributor {

/*
 * Bundle of ideal service layer nodes for a bucket.
 */
class IdealServiceLayerNodesBundle {
    std::vector<uint16_t> _available_nodes;
    std::vector<uint16_t> _available_nonretired_nodes;
    std::vector<uint16_t> _available_nonretired_or_maintenance_nodes;
public:
    IdealServiceLayerNodesBundle() noexcept;
    ~IdealServiceLayerNodesBundle();

    void set_available_nodes(std::vector<uint16_t> available_nodes) { _available_nodes = std::move(available_nodes); }
    void set_available_nonretired_nodes(std::vector<uint16_t> available_nonretired_nodes) { _available_nonretired_nodes = std::move(available_nonretired_nodes); }
    void set_available_nonretired_or_maintenance_nodes(std::vector<uint16_t> available_nonretired_or_maintenance_nodes) { _available_nonretired_or_maintenance_nodes = std::move(available_nonretired_or_maintenance_nodes); }
    std::vector<uint16_t> get_available_nodes() const { return _available_nodes; }
    std::vector<uint16_t> get_available_nonretired_nodes() const { return _available_nonretired_nodes; }
    std::vector<uint16_t> get_available_nonretired_or_maintenance_nodes() const { return _available_nonretired_or_maintenance_nodes; }
};

}

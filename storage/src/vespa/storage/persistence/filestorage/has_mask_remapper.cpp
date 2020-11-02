// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "has_mask_remapper.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <cassert>

namespace storage {

HasMaskRemapper::HasMaskRemapper(const std::vector<api::MergeBucketCommand::Node> &all_nodes,
                                 const std::vector<api::MergeBucketCommand::Node> &nodes)
    : _mask_remap(),
      _all_remapped(0u)
{
    if (nodes != all_nodes) {
        vespalib::hash_map<uint32_t, uint32_t> node_index_to_mask(all_nodes.size());
        uint16_t mask = 1u;
        for (const auto& node : all_nodes) {
            node_index_to_mask[node.index] = mask;
            mask <<= 1;
        }
        _mask_remap.reserve(nodes.size());
        for (const auto& node : nodes) {
            mask = node_index_to_mask[node.index];
            assert(mask != 0u);
            _mask_remap.push_back(mask);
            _all_remapped |= mask;
        }
    } else {
        _all_remapped = ((1u << all_nodes.size()) - 1);
    }
}

HasMaskRemapper::~HasMaskRemapper() = default;

uint16_t
HasMaskRemapper::operator()(uint16_t mask) const
{
    if (!_mask_remap.empty()) {
        uint16_t new_mask = 0u;
        for (uint32_t i = 0u; i < _mask_remap.size(); ++i) {
            if ((mask & (1u << i)) != 0u) {
                new_mask |= _mask_remap[i];
            }
        }
        mask = new_mask;
    } else {
        mask &= _all_remapped;
    }
    return mask;
}

uint16_t
HasMaskRemapper::operator()(uint16_t mask, uint16_t keep_from_full_mask) const
{
    return (operator()(mask) | (keep_from_full_mask & ~_all_remapped));
}

}

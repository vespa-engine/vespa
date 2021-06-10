// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "min_replica_provider.h"

namespace storage::distributor {

void
merge_min_replica_stats(std::unordered_map<uint16_t, uint32_t>& dest,
                        const std::unordered_map<uint16_t, uint32_t>& src)
{
    for (const auto& entry : src) {
        auto node_index = entry.first;
        dest[node_index] += entry.second;
    }
}

}

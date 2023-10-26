// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "min_replica_provider.h"

namespace storage::distributor {

void
merge_min_replica_stats(MinReplicaMap & dest, const MinReplicaMap & src)
{
    for (const auto& entry : src) {
        auto node_index = entry.first;
        auto itr = dest.find(node_index);
        auto new_min_replica = (itr != dest.end()) ? std::min(itr->second, entry.second) : entry.second;
        dest[node_index] = new_min_replica;
    }
}

}

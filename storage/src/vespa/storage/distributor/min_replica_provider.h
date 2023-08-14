// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hash_map.h>

namespace storage::distributor {

using MinReplicaMap = vespalib::hash_map<uint16_t, uint32_t>;

class MinReplicaProvider
{
public:
    virtual ~MinReplicaProvider() = default;

    /**
     * Get a snapshot of the minimum bucket replica for each of the nodes.
     *
     * Can be called at any time after registration from another thread context
     * and the call must thus be thread safe and data race free.
     */
    virtual MinReplicaMap getMinReplica() const = 0;
};

void merge_min_replica_stats(MinReplicaMap & dest, const MinReplicaMap & src);

}


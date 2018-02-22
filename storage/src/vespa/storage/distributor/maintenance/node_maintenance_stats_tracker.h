// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <unordered_map>
#include <iosfwd>
#include <stdint.h>
#include <vespa/document/bucket/bucketspace.h>

namespace storage {
namespace distributor {

struct NodeMaintenanceStats
{
    uint64_t movingOut;
    uint64_t syncing;
    uint64_t copyingIn;
    uint64_t copyingOut;

    NodeMaintenanceStats()
        : movingOut(0), syncing(0), copyingIn(0), copyingOut(0)
    {}

    NodeMaintenanceStats(uint64_t movingOut_, uint64_t syncing_, uint64_t copyingIn_, uint64_t copyingOut_)
        : movingOut(movingOut_), syncing(syncing_), copyingIn(copyingIn_), copyingOut(copyingOut_)
    {}

    bool operator==(const NodeMaintenanceStats& other) const noexcept {
        return (movingOut == other.movingOut
                && syncing == other.syncing
                && copyingIn == other.copyingIn
                && copyingOut == other.copyingOut);
    }
};

std::ostream& operator<<(std::ostream&, const NodeMaintenanceStats&);

class NodeMaintenanceStatsTracker
{
public:
    using BucketSpacesStats = std::unordered_map<document::BucketSpace, NodeMaintenanceStats, document::BucketSpace::hash>;
    using PerNodeStats = std::unordered_map<uint16_t, BucketSpacesStats>;

private:
    PerNodeStats _stats;
    static const NodeMaintenanceStats _emptyNodeMaintenanceStats;

public:
    NodeMaintenanceStatsTracker();
    ~NodeMaintenanceStatsTracker();
    void incMovingOut(uint16_t node, document::BucketSpace bucketSpace) {
        ++_stats[node][bucketSpace].movingOut;
    }

    void incSyncing(uint16_t node, document::BucketSpace bucketSpace) {
        ++_stats[node][bucketSpace].syncing;
    }

    void incCopyingIn(uint16_t node, document::BucketSpace bucketSpace) {
        ++_stats[node][bucketSpace].copyingIn;
    }

    void incCopyingOut(uint16_t node, document::BucketSpace bucketSpace) {
        ++_stats[node][bucketSpace].copyingOut;
    }

    /**
     * Returned statistics for a given node index and bucket space, or all zero statistics
     * if none have been recorded yet
     */
    const NodeMaintenanceStats& forNode(uint16_t node, document::BucketSpace bucketSpace) const {
        auto nodeItr = _stats.find(node);
        if (nodeItr != _stats.end()) {
            auto bucketSpaceItr = nodeItr->second.find(bucketSpace);
            if (bucketSpaceItr != nodeItr->second.end()) {
                return bucketSpaceItr->second;
            }
        }
        return _emptyNodeMaintenanceStats;
    }

    const PerNodeStats& perNodeStats() const {
        return _stats;
    }
};

} // distributor
} // storage


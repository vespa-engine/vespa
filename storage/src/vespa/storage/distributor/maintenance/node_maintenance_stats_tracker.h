// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <unordered_map>
#include <iosfwd>
#include <stdint.h>

namespace storage {
namespace distributor {

struct NodeMaintenanceStats
{
    uint64_t movingOut {0};
    uint64_t syncing {0};
    uint64_t copyingIn {0};
    uint64_t copyingOut {0};

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
    std::unordered_map<uint16_t, NodeMaintenanceStats> _stats;
    static const NodeMaintenanceStats _emptyStats;
public:
    NodeMaintenanceStatsTracker();
    ~NodeMaintenanceStatsTracker();
    void incMovingOut(uint16_t node) {
        ++_stats[node].movingOut;
    }

    void incSyncing(uint16_t node) {
        ++_stats[node].syncing;
    }

    void incCopyingIn(uint16_t node) {
        ++_stats[node].copyingIn;
    }

    void incCopyingOut(uint16_t node) {
        ++_stats[node].copyingOut;
    }

    /**
     * Returned statistics for a given node index, or all zero statistics
     * if none have been recorded yet
     */
    const NodeMaintenanceStats& forNode(uint16_t node) const {
        auto iter = _stats.find(node);
        return (iter != _stats.end() ? iter->second : _emptyStats);
    }
};

} // distributor
} // storage


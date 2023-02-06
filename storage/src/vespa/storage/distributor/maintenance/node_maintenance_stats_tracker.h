// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <unordered_map>
#include <vespa/document/bucket/bucketspace.h>
#include <vespa/vespalib/util/time.h>

namespace storage::distributor {

struct NodeMaintenanceStats
{
    uint64_t movingOut;
    uint64_t syncing;
    uint64_t copyingIn;
    uint64_t copyingOut;
    uint64_t total;

    constexpr NodeMaintenanceStats() noexcept
        : movingOut(0), syncing(0), copyingIn(0), copyingOut(0), total(0)
    {}

    constexpr NodeMaintenanceStats(uint64_t movingOut_, uint64_t syncing_, uint64_t copyingIn_,
                                   uint64_t copyingOut_, uint64_t total_) noexcept
        : movingOut(movingOut_), syncing(syncing_),
          copyingIn(copyingIn_), copyingOut(copyingOut_),
          total(total_)
    {}

    bool operator==(const NodeMaintenanceStats& other) const noexcept {
        return (movingOut == other.movingOut
                && syncing == other.syncing
                && copyingIn == other.copyingIn
                && copyingOut == other.copyingOut
                && total == other.total);
    }
    bool operator!=(const NodeMaintenanceStats& other) const noexcept {
        return !(*this == other);
    }

    void merge(const NodeMaintenanceStats& rhs);
};

std::ostream& operator<<(std::ostream&, const NodeMaintenanceStats&);

class NodeMaintenanceStatsTracker
{
public:
    using BucketSpacesStats = std::unordered_map<document::BucketSpace, NodeMaintenanceStats, document::BucketSpace::hash>;
    using PerNodeStats = std::unordered_map<uint16_t, BucketSpacesStats>;

private:
    PerNodeStats         _node_stats;
    NodeMaintenanceStats _total_stats;
    vespalib::duration   _max_observed_time_since_last_gc;

    static const NodeMaintenanceStats _emptyNodeMaintenanceStats;

public:
    NodeMaintenanceStatsTracker();
    ~NodeMaintenanceStatsTracker();

    void incMovingOut(uint16_t node, document::BucketSpace bucketSpace) {
        ++_node_stats[node][bucketSpace].movingOut;
        ++_total_stats.movingOut;
    }

    void incSyncing(uint16_t node, document::BucketSpace bucketSpace) {
        ++_node_stats[node][bucketSpace].syncing;
        ++_total_stats.syncing;
    }

    void incCopyingIn(uint16_t node, document::BucketSpace bucketSpace) {
        ++_node_stats[node][bucketSpace].copyingIn;
        ++_total_stats.copyingIn;
    }

    void incCopyingOut(uint16_t node, document::BucketSpace bucketSpace) {
        ++_node_stats[node][bucketSpace].copyingOut;
        ++_total_stats.copyingOut;
    }

    void incTotal(uint16_t node, document::BucketSpace bucketSpace) {
        ++_node_stats[node][bucketSpace].total;
        ++_total_stats.total;
    }

    void update_observed_time_since_last_gc(vespalib::duration time_since_gc) noexcept {
        _max_observed_time_since_last_gc = std::max(time_since_gc, _max_observed_time_since_last_gc);
    }

    /**
     * Returned statistics for a given node index and bucket space, or all zero statistics
     * if none have been recorded yet
     */
    const NodeMaintenanceStats& forNode(uint16_t node, document::BucketSpace bucketSpace) const {
        auto nodeItr = _node_stats.find(node);
        if (nodeItr != _node_stats.end()) {
            auto bucketSpaceItr = nodeItr->second.find(bucketSpace);
            if (bucketSpaceItr != nodeItr->second.end()) {
                return bucketSpaceItr->second;
            }
        }
        return _emptyNodeMaintenanceStats;
    }

    const PerNodeStats& perNodeStats() const {
        return _node_stats;
    }

    // Note: the total statistics are across all replicas across all buckets across all bucket spaces.
    // That means it's possible for a single bucket to count more than once, up to once per replica.
    // So this should not be treated as a bucket-level statistic.
    const NodeMaintenanceStats& total_replica_stats() const noexcept {
        return _total_stats;
    }

    vespalib::duration max_observed_time_since_last_gc() const noexcept {
        return _max_observed_time_since_last_gc;
    }

    bool operator==(const NodeMaintenanceStatsTracker& rhs) const {
        return ((_node_stats == rhs._node_stats) &&
                (_max_observed_time_since_last_gc == rhs._max_observed_time_since_last_gc));
    }
    void merge(const NodeMaintenanceStatsTracker& rhs);
};

} // storage::distributor

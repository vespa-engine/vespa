// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/stllike/hash_map.h>

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

    void merge(const NodeMaintenanceStats& rhs) noexcept {
        movingOut  += rhs.movingOut;
        syncing    += rhs.syncing;
        copyingIn  += rhs.copyingIn;
        copyingOut += rhs.copyingOut;
        total      += rhs.total;
    }
};

std::ostream& operator<<(std::ostream&, const NodeMaintenanceStats&);

class NodeMaintenanceStatsTracker
{
public:
    class BucketSpaceAndNode {
    public:
        BucketSpaceAndNode(uint16_t node_in, document::BucketSpace bucketSpace_in) noexcept
            : _bucketSpace(bucketSpace_in),
              _node(node_in)
        {}
        uint32_t hash() const noexcept { return (uint32_t(_node) << 2) | (_bucketSpace.getId() & 0x3); }
        bool operator == (const BucketSpaceAndNode & b) const noexcept {
            return (_bucketSpace == b._bucketSpace) && (_node == b._node);
        }
        document::BucketSpace bucketSpace() const noexcept { return _bucketSpace; }
        uint16_t node() const noexcept { return _node; }
    private:
        document::BucketSpace _bucketSpace;
        uint16_t              _node;
    };
    using PerNodeStats = vespalib::hash_map<BucketSpaceAndNode, NodeMaintenanceStats>;

private:
    PerNodeStats         _node_stats;
    NodeMaintenanceStats _total_stats;
    vespalib::duration   _max_observed_time_since_last_gc;

    static const NodeMaintenanceStats _emptyNodeMaintenanceStats;

    NodeMaintenanceStats & stats(uint16_t node, document::BucketSpace bucketSpace);
    const NodeMaintenanceStats & stats(uint16_t node, document::BucketSpace bucketSpace) const noexcept;
public:
    NodeMaintenanceStatsTracker() noexcept;
    NodeMaintenanceStatsTracker(NodeMaintenanceStatsTracker &&) noexcept;
    NodeMaintenanceStatsTracker & operator =(NodeMaintenanceStatsTracker &&) noexcept;
    NodeMaintenanceStatsTracker(const NodeMaintenanceStatsTracker &);
    ~NodeMaintenanceStatsTracker();
    void reset(size_t nodes);
    size_t numNodes() const { return _node_stats.size(); }

    void incMovingOut(uint16_t node, document::BucketSpace bucketSpace) {
        ++stats(node, bucketSpace).movingOut;
        ++_total_stats.movingOut;
    }

    void incSyncing(uint16_t node, document::BucketSpace bucketSpace) {
        ++stats(node, bucketSpace).syncing;
        ++_total_stats.syncing;
    }

    void incCopyingIn(uint16_t node, document::BucketSpace bucketSpace) {
        ++stats(node, bucketSpace).copyingIn;
        ++_total_stats.copyingIn;
    }

    void incCopyingOut(uint16_t node, document::BucketSpace bucketSpace) {
        ++stats(node, bucketSpace).copyingOut;
        ++_total_stats.copyingOut;
    }

    void incTotal(uint16_t node, document::BucketSpace bucketSpace) {
        ++stats(node, bucketSpace).total;
        ++_total_stats.total;
    }

    void update_observed_time_since_last_gc(vespalib::duration time_since_gc) noexcept {
        _max_observed_time_since_last_gc = std::max(time_since_gc, _max_observed_time_since_last_gc);
    }

    /**
     * Returned statistics for a given node index and bucket space, or all zero statistics
     * if none have been recorded yet
     */
    const NodeMaintenanceStats& forNode(uint16_t node, document::BucketSpace bucketSpace) const noexcept;

    const PerNodeStats& perNodeStats() const noexcept {
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

    bool operator==(const NodeMaintenanceStatsTracker& rhs) const noexcept;
    void merge(const NodeMaintenanceStatsTracker& rhs);
};

} // storage::distributor

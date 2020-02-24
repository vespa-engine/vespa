// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/config/config-stor-distributormanager.h>

#include <unordered_map>

namespace storage::distributor {

class DistributorMetricSet;
class IdealStateMetricSet;

class BucketDBMetricUpdater {
public:
    /** Bucket statistics for a single database iteration */
    struct Stats {
        uint64_t _docCount;
        uint64_t _byteCount;
        uint64_t _tooFewCopies;
        uint64_t _tooManyCopies;
        uint64_t _noTrusted;
        uint64_t _totalBuckets;

        Stats();
        Stats(const Stats &rhs);
        ~Stats() { }

        Stats &operator=(const Stats &rhs) = default;

        /**
         * For each node N, look at all the buckets that have or should have a
         * bucket copy on that node.  For each of these buckets, there is a
         * number of trusted copies.  Take the bucket with the least number of
         * trusted copies C.  _minBucketReplica[N] equals this C.
         *
         * C can be used to determine the effect on replication if storage node
         * N is taken out for maintenance.
         *
         * If we could rely 100% on our concept of "trusted copies", then a more
         * accurate measure for any effect on replication would be to only look
         * at the buckets for which node N has a trusted copy.
         *
         * Note: If no buckets have been found for a node, that node is not in
         * this map.
         */
        std::unordered_map<uint16_t, uint32_t> _minBucketReplica;

        /**
         * Propagate state values to the appropriate metric values.
         */
        void propagateMetrics(IdealStateMetricSet&, DistributorMetricSet&);
    };

    using ReplicaCountingMode = vespa::config::content::core::StorDistributormanagerConfig::MinimumReplicaCountingMode;

private:
    Stats _workingStats;
    Stats _lastCompleteStats;
    ReplicaCountingMode _replicaCountingMode;
    bool _hasCompleteStats;

public:
    BucketDBMetricUpdater();

    ~BucketDBMetricUpdater();

    void setMinimumReplicaCountingMode(ReplicaCountingMode mode) noexcept {
        _replicaCountingMode = mode;
    }
    ReplicaCountingMode getMinimumReplicaCountingMode() const noexcept {
        return _replicaCountingMode;
    }

    void visit(const BucketDatabase::Entry& e, uint32_t redundancy);
    /**
     * Reset all values in current working state to zero.
     */
    void reset();
    /**
     * Called after an entire DB iteration round has been completed. Updates
     * last complete state with current working state.
     *
     * If reset==true, resets current working state to all zero. Using anything
     * but true here is primarily for unit testing.
     */
    void completeRound(bool resetWorkingStats = true);

    /**
     * Returns true iff completeRound() has been called at least once.
     */
    bool hasCompletedRound() const {
        return _hasCompleteStats;
    }

    Stats getLastCompleteStats() const {
        return _lastCompleteStats;
    }

private:
    void updateMinReplicationStats(const BucketDatabase::Entry& entry, uint32_t trustedCopies);

    void resetStats();
};

} // storage::distributor

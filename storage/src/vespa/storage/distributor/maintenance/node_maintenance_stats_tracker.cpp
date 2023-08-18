// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "node_maintenance_stats_tracker.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_map_equal.hpp>
#include <ostream>

namespace storage::distributor {

const NodeMaintenanceStats NodeMaintenanceStatsTracker::_emptyNodeMaintenanceStats;

NodeMaintenanceStats &
NodeMaintenanceStatsTracker::stats(uint16_t node, document::BucketSpace bucketSpace) {
    return _node_stats[BucketSpaceAndNode(node, bucketSpace)];
}

const NodeMaintenanceStats &
NodeMaintenanceStatsTracker::stats(uint16_t node, document::BucketSpace bucketSpace) const noexcept {
    auto nodeItr = _node_stats.find(BucketSpaceAndNode(node, bucketSpace));
    return (nodeItr != _node_stats.end()) ? nodeItr->second : _emptyNodeMaintenanceStats;
}

const NodeMaintenanceStats&
NodeMaintenanceStatsTracker::forNode(uint16_t node, document::BucketSpace bucketSpace) const noexcept {
    return stats(node, bucketSpace);
}

bool
NodeMaintenanceStatsTracker::operator==(const NodeMaintenanceStatsTracker& rhs) const noexcept {
    return ((_node_stats == rhs._node_stats) &&
            (_max_observed_time_since_last_gc == rhs._max_observed_time_since_last_gc));
}

void
NodeMaintenanceStatsTracker::merge(const NodeMaintenanceStatsTracker& rhs)
{
    for (const auto& entry : rhs._node_stats) {
        auto key = entry.first;
        _node_stats[key].merge(entry.second);
    }
    _max_observed_time_since_last_gc = std::max(_max_observed_time_since_last_gc,
                                                rhs._max_observed_time_since_last_gc);
}

std::ostream&
operator<<(std::ostream& os, const NodeMaintenanceStats& stats)
{
    os << "NodeStats("
       << "movingOut="   << stats.movingOut
       << ",syncing="    << stats.syncing
       << ",copyingIn="  << stats.copyingIn
       << ",copyingOut=" << stats.copyingOut
       << ",total="      << stats.total
       << ")";
    return os;
}

NodeMaintenanceStatsTracker::NodeMaintenanceStatsTracker() noexcept
    : _node_stats(),
      _total_stats(),
      _max_observed_time_since_last_gc(0)
{}

NodeMaintenanceStatsTracker::NodeMaintenanceStatsTracker(NodeMaintenanceStatsTracker &&) noexcept = default;
NodeMaintenanceStatsTracker & NodeMaintenanceStatsTracker::operator =(NodeMaintenanceStatsTracker &&) noexcept = default;
NodeMaintenanceStatsTracker::NodeMaintenanceStatsTracker(const NodeMaintenanceStatsTracker &) = default;
NodeMaintenanceStatsTracker::~NodeMaintenanceStatsTracker() = default;

void
NodeMaintenanceStatsTracker::reset(size_t nodes) {
    _node_stats.clear();
    _node_stats.resize(nodes);
    _total_stats = NodeMaintenanceStats();
    _max_observed_time_since_last_gc = vespalib::duration::zero();
}

}


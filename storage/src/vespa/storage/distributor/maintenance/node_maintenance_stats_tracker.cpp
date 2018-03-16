// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "node_maintenance_stats_tracker.h"
#include <ostream>

namespace storage::distributor {

const NodeMaintenanceStats NodeMaintenanceStatsTracker::_emptyNodeMaintenanceStats;

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

NodeMaintenanceStatsTracker::NodeMaintenanceStatsTracker() {}
NodeMaintenanceStatsTracker::~NodeMaintenanceStatsTracker() {}

}


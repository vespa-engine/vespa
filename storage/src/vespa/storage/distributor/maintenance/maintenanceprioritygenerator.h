// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/storage/distributor/maintenance/prioritizedbucket.h>
#include <vespa/storage/distributor/maintenance/maintenancepriorityandtype.h>
#include <vespa/storage/distributor/maintenance/node_maintenance_stats_tracker.h>

namespace storage::distributor {

class MaintenancePriorityGenerator
{
public:
    virtual ~MaintenancePriorityGenerator() = default;

    virtual MaintenancePriorityAndType prioritize(
            const document::Bucket &bucket,
            NodeMaintenanceStatsTracker &statsTarcker) const = 0;
};

}

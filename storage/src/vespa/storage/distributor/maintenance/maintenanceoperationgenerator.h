// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/storage/distributor/maintenance/maintenanceoperation.h>
#include <vespa/storage/distributor/maintenance/node_maintenance_stats_tracker.h>

namespace storage::distributor {

class MaintenanceOperationGenerator
{
public:
    virtual ~MaintenanceOperationGenerator() = default;

    /**
     * Generate and return the highest prioritized maintenance operation for
     * the given bucket. If the bucket does not need maintenance, a nullptr
     * shared_ptr is returned.
     */
    virtual MaintenanceOperation::SP generate(const document::Bucket &bucket) const = 0;

    /**
     * Generate all possible maintenance operations for the given bucket and
     * return these, ordered by priority in decreasing order. If the bucket
     * does not need maintenance, the returned vector will be empty.
     */
    virtual std::vector<MaintenanceOperation::SP> generateAll(
                const document::Bucket &bucket,
                NodeMaintenanceStatsTracker &statsTracker) const = 0;

    /**
     * Convenience wrapper around generateAll() for when there's no need for
     * an explicit stats tracker
     */
    std::vector<MaintenanceOperation::SP> generateAll(const document::Bucket &bucket) const
    {
        NodeMaintenanceStatsTracker dummyTracker;
        return generateAll(bucket, dummyTracker);
    }
};

} // storage::distributor

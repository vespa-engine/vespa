// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/storage/distributor/maintenance/maintenanceoperation.h>
#include <vespa/storage/distributor/maintenance/node_maintenance_stats_tracker.h>

namespace storage {
namespace distributor {

class MaintenanceOperationGenerator
{
public:
    virtual ~MaintenanceOperationGenerator() {}

    /**
     * Generate and return the highest prioritized maintenance operation for
     * the given bucket. If the bucket does not need maintenance, a nullptr
     * shared_ptr is returned.
     */
    virtual MaintenanceOperation::SP generate(
                const document::BucketId&) const = 0;

    /**
     * Generate all possible maintenance operations for the given bucket and
     * return these, ordered by priority in decreasing order. If the bucket
     * does not need maintenance, the returned vector will be empty.
     */
    virtual std::vector<MaintenanceOperation::SP> generateAll(
                const document::BucketId&,
                NodeMaintenanceStatsTracker&) const = 0;

    /**
     * Convenience wrapper around generateAll() for when there's no need for
     * an explicit stats tracker
     */
    std::vector<MaintenanceOperation::SP> generateAll(
                const document::BucketId& bucketId) const
    {
        NodeMaintenanceStatsTracker dummyTracker;
        return generateAll(bucketId, dummyTracker);
    }
};

} // distributor
} // storage

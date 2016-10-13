// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <vespa/storage/distributor/maintenance/maintenancescanner.h>
#include <vespa/storage/distributor/maintenance/bucketprioritydatabase.h>
#include <vespa/storage/distributor/maintenance/maintenanceprioritygenerator.h>
#include <vespa/storage/distributor/maintenance/node_maintenance_stats_tracker.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>

namespace storage {
namespace distributor {

class SimpleMaintenanceScanner : public MaintenanceScanner
{
public:
    struct GlobalMaintenanceStats {
        std::vector<uint64_t> pending;

        GlobalMaintenanceStats()
            : pending(MaintenanceOperation::OPERATION_COUNT)
        {
        }
    };
    struct PendingMaintenanceStats {
        GlobalMaintenanceStats global;
        NodeMaintenanceStatsTracker perNodeStats;
    };
private:
    BucketPriorityDatabase& _bucketPriorityDb;
    const MaintenancePriorityGenerator& _priorityGenerator;
    const BucketDatabase& _bucketDb;
    document::BucketId _bucketCursor;
    PendingMaintenanceStats _pendingMaintenance;
public:
    SimpleMaintenanceScanner(BucketPriorityDatabase& bucketPriorityDb,
                             const MaintenancePriorityGenerator& priorityGenerator,
                             const BucketDatabase& bucketDb)
        : _bucketPriorityDb(bucketPriorityDb),
          _priorityGenerator(priorityGenerator),
          _bucketDb(bucketDb),
          _bucketCursor()
    {}

    ScanResult scanNext();

    void reset();

    // TODO: move out into own interface!
    void prioritizeBucket(const document::BucketId& id);

    const PendingMaintenanceStats& getPendingMaintenanceStats() const {
        return _pendingMaintenance;
    }
private:
    SimpleMaintenanceScanner(const SimpleMaintenanceScanner&);
    SimpleMaintenanceScanner& operator=(const SimpleMaintenanceScanner&);
};

std::ostream&
operator<<(std::ostream&,
           const SimpleMaintenanceScanner::GlobalMaintenanceStats&);

}
}


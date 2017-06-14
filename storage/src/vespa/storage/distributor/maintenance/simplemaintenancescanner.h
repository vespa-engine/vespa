// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "maintenancescanner.h"
#include "bucketprioritydatabase.h"
#include "maintenanceprioritygenerator.h"
#include "node_maintenance_stats_tracker.h"
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
        { }
    };
    struct PendingMaintenanceStats {
        PendingMaintenanceStats();
        PendingMaintenanceStats(const PendingMaintenanceStats &);
        PendingMaintenanceStats &operator = (const PendingMaintenanceStats &);
        ~PendingMaintenanceStats();
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
    SimpleMaintenanceScanner(const SimpleMaintenanceScanner&) = delete;
    SimpleMaintenanceScanner& operator=(const SimpleMaintenanceScanner&) = delete;
    ~SimpleMaintenanceScanner();

    ScanResult scanNext() override;
    void reset() override;

    // TODO: move out into own interface!
    void prioritizeBucket(const document::BucketId& id);

    const PendingMaintenanceStats& getPendingMaintenanceStats() const {
        return _pendingMaintenance;
    }
};

std::ostream&
operator<<(std::ostream&, const SimpleMaintenanceScanner::GlobalMaintenanceStats&);

}
}

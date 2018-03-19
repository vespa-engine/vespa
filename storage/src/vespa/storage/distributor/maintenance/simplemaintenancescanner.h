// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "maintenancescanner.h"
#include "bucketprioritydatabase.h"
#include "maintenanceprioritygenerator.h"
#include "node_maintenance_stats_tracker.h"
#include <vespa/storage/distributor/distributor_bucket_space_repo.h>

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
    const DistributorBucketSpaceRepo &_bucketSpaceRepo;
    DistributorBucketSpaceRepo::BucketSpaceMap::const_iterator _bucketSpaceItr;
    document::BucketId _bucketCursor;
    PendingMaintenanceStats _pendingMaintenance;

    void countBucket(document::BucketSpace bucketSpace, const BucketInfo &info);
public:
    SimpleMaintenanceScanner(BucketPriorityDatabase& bucketPriorityDb,
                             const MaintenancePriorityGenerator& priorityGenerator,
                             const DistributorBucketSpaceRepo& bucketSpaceRepo);
    SimpleMaintenanceScanner(const SimpleMaintenanceScanner&) = delete;
    SimpleMaintenanceScanner& operator=(const SimpleMaintenanceScanner&) = delete;
    ~SimpleMaintenanceScanner();

    ScanResult scanNext() override;
    void reset() override;

    // TODO: move out into own interface!
    void prioritizeBucket(const document::Bucket &id);

    const PendingMaintenanceStats& getPendingMaintenanceStats() const {
        return _pendingMaintenance;
    }
};

std::ostream&
operator<<(std::ostream&, const SimpleMaintenanceScanner::GlobalMaintenanceStats&);

}
}

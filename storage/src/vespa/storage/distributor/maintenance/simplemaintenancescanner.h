// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "maintenancescanner.h"
#include "bucketprioritydatabase.h"
#include "maintenanceprioritygenerator.h"
#include "node_maintenance_stats_tracker.h"
#include <vespa/storage/distributor/distributor_bucket_space_repo.h>

namespace storage::distributor {

class SimpleMaintenanceScanner : public MaintenanceScanner
{
public:
    struct GlobalMaintenanceStats {
        std::array<uint64_t, MaintenanceOperation::OPERATION_COUNT> pending;

        GlobalMaintenanceStats() noexcept
            : pending()
        { }

        bool operator==(const GlobalMaintenanceStats& rhs) const noexcept;
        void merge(const GlobalMaintenanceStats& rhs) noexcept;
    };
    struct PendingMaintenanceStats {
        PendingMaintenanceStats() noexcept;
        PendingMaintenanceStats(const PendingMaintenanceStats &);
        PendingMaintenanceStats &operator = (const PendingMaintenanceStats &) = delete;
        PendingMaintenanceStats(PendingMaintenanceStats &&) noexcept;
        PendingMaintenanceStats &operator = (PendingMaintenanceStats &&) noexcept;
        ~PendingMaintenanceStats();
        [[nodiscard]] PendingMaintenanceStats fetch_and_reset();
        GlobalMaintenanceStats      global;
        NodeMaintenanceStatsTracker perNodeStats;

        void merge(const PendingMaintenanceStats& rhs);
    };
private:
    BucketPriorityDatabase&                                    _bucketPriorityDb;
    const MaintenancePriorityGenerator&                        _priorityGenerator;
    const DistributorBucketSpaceRepo&                          _bucketSpaceRepo;
    DistributorBucketSpaceRepo::BucketSpaceMap::const_iterator _bucketSpaceItr;
    document::BucketId                                         _bucketCursor;
    PendingMaintenanceStats                                    _pendingMaintenance;

    void countBucket(document::BucketSpace bucketSpace, const BucketInfo &info);
public:
    SimpleMaintenanceScanner(BucketPriorityDatabase& bucketPriorityDb,
                             const MaintenancePriorityGenerator& priorityGenerator,
                             const DistributorBucketSpaceRepo& bucketSpaceRepo);
    SimpleMaintenanceScanner(const SimpleMaintenanceScanner&) = delete;
    SimpleMaintenanceScanner& operator=(const SimpleMaintenanceScanner&) = delete;
    ~SimpleMaintenanceScanner() override;

    ScanResult scanNext() override;
    [[nodiscard]] PendingMaintenanceStats fetch_and_reset();

    // TODO: move out into own interface!
    void prioritizeBucket(const document::Bucket &id);

    // TODO Only for testing
    const PendingMaintenanceStats& getPendingMaintenanceStats() const noexcept {
        return _pendingMaintenance;
    }
};

std::ostream&
operator<<(std::ostream&, const SimpleMaintenanceScanner::GlobalMaintenanceStats&);

}

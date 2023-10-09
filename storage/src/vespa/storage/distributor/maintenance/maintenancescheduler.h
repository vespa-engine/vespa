// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/maintenance/prioritizedbucket.h>
#include <vespa/storage/distributor/maintenance/simplemaintenancescanner.h>
#include <vespa/storage/distributor/operationstarter.h>

namespace storage::distributor {

class MaintenanceOperationGenerator;
class BucketPriorityDatabase;
class PendingWindowChecker;

class MaintenanceScheduler
{
public:
    enum SchedulingMode {
        RECOVERY_SCHEDULING_MODE,
        NORMAL_SCHEDULING_MODE
    };

    using WaitTimeMs = int;

    MaintenanceScheduler(MaintenanceOperationGenerator& operationGenerator,
                         BucketPriorityDatabase& priorityDb,
                         const PendingWindowChecker& pending_window_checker,
                         OperationStarter& operationStarter);

    WaitTimeMs tick(SchedulingMode currentMode);

    void set_implicitly_clear_priority_on_schedule(bool implicitly_clear) noexcept {
        _implicitly_clear_priority_on_schedule = implicitly_clear;
    }
    [[nodiscard]] bool implicitly_clear_priority_on_schedule() const noexcept {
        return _implicitly_clear_priority_on_schedule;
    }

private:
    MaintenanceScheduler(const MaintenanceScheduler&);
    MaintenanceScheduler& operator=(const MaintenanceScheduler&);

    PrioritizedBucket getMostImportantBucket();
    bool possibleToSchedule(const PrioritizedBucket& bucket, SchedulingMode currentMode) const;
    bool possibleToScheduleInEmergency(const PrioritizedBucket& bucket) const;
    void clearPriority(const PrioritizedBucket& bucket);
    bool startOperation(const PrioritizedBucket& bucket);
    OperationStarter::Priority convertToOperationPriority(MaintenancePriority::Priority priority) const;
    bool has_bucket_activation_priority(const PrioritizedBucket&) const noexcept;

    MaintenanceOperationGenerator& _operationGenerator;
    BucketPriorityDatabase&        _priorityDb;
    const PendingWindowChecker&    _pending_window_checker;
    OperationStarter&              _operationStarter;
    bool                           _implicitly_clear_priority_on_schedule;
};

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/maintenance/prioritizedbucket.h>
#include <vespa/storage/distributor/maintenance/simplemaintenancescanner.h>
#include <vespa/storage/distributor/operationstarter.h>

namespace storage::distributor {

class MaintenanceOperationGenerator;
class BucketPriorityDatabase;

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
                         OperationStarter& operationStarter);

    WaitTimeMs tick(SchedulingMode currentMode);

private:
    MaintenanceScheduler(const MaintenanceScheduler&);
    MaintenanceScheduler& operator=(const MaintenanceScheduler&);

    PrioritizedBucket getMostImportantBucket();
    bool possibleToSchedule(const PrioritizedBucket& bucket, SchedulingMode currentMode) const;
    bool possibleToScheduleInEmergency(const PrioritizedBucket& bucket) const;
    void clearPriority(const PrioritizedBucket& bucket);
    bool startOperation(const PrioritizedBucket& bucket);
    OperationStarter::Priority convertToOperationPriority(
            MaintenancePriority::Priority priority) const;

    MaintenanceOperationGenerator& _operationGenerator;
    BucketPriorityDatabase& _priorityDb;
    OperationStarter& _operationStarter;
};

}

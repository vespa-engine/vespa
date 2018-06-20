// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maintenancescheduler.h"
#include "maintenanceoperationgenerator.h"
#include <vespa/storage/distributor/operationstarter.h>
#include <vespa/storage/distributor/operations/idealstate/idealstateoperation.h>

#include <vespa/log/log.h>
LOG_SETUP(".storage.distributor.maintenance.maintenance_scheduler");

namespace storage::distributor {

MaintenanceScheduler::MaintenanceScheduler(
        MaintenanceOperationGenerator& operationGenerator,
        BucketPriorityDatabase& priorityDb,
        OperationStarter& operationStarter)
    : _operationGenerator(operationGenerator),
      _priorityDb(priorityDb),
      _operationStarter(operationStarter)
{
}

PrioritizedBucket
MaintenanceScheduler::getMostImportantBucket()
{
    BucketPriorityDatabase::const_iterator mostImportant(_priorityDb.begin());
    if (mostImportant == _priorityDb.end()) {
        return PrioritizedBucket::INVALID;
    }
    return *mostImportant;
}

MaintenanceScheduler::WaitTimeMs
MaintenanceScheduler::tick(SchedulingMode currentMode)
{
    PrioritizedBucket mostImportant(getMostImportantBucket());

    if (!possibleToSchedule(mostImportant, currentMode)) {
        return WaitTimeMs(1);
    }
    if (!startOperation(mostImportant)) {
        return WaitTimeMs(1);
    }
    clearPriority(mostImportant);
    return WaitTimeMs(0);
}

bool
MaintenanceScheduler::possibleToSchedule(const PrioritizedBucket& bucket,
                                         SchedulingMode currentMode) const
{
    if (currentMode == RECOVERY_SCHEDULING_MODE) {
        return (bucket.valid()
                && possibleToScheduleInEmergency(bucket));
    } else {
        return bucket.valid();
    }
}

bool
MaintenanceScheduler::possibleToScheduleInEmergency(
        const PrioritizedBucket& bucket) const
{
    return bucket.moreImportantThan(MaintenancePriority::HIGH);
}

void
MaintenanceScheduler::clearPriority(const PrioritizedBucket& bucket)
{
    _priorityDb.setPriority(PrioritizedBucket(bucket.getBucket(),
                                              MaintenancePriority::NO_MAINTENANCE_NEEDED));
}

OperationStarter::Priority
MaintenanceScheduler::convertToOperationPriority(MaintenancePriority::Priority priority) const
{
    switch (priority) {
    case MaintenancePriority::VERY_LOW:
        return OperationStarter::Priority(200);
    case MaintenancePriority::LOW:
        return OperationStarter::Priority(150);
    case MaintenancePriority::MEDIUM:
        return OperationStarter::Priority(100);
    case MaintenancePriority::HIGH:
        return OperationStarter::Priority(50);
    case MaintenancePriority::VERY_HIGH:
        return OperationStarter::Priority(0);
    default:
        LOG_ABORT("should not be reached");
    }
}

bool
MaintenanceScheduler::startOperation(const PrioritizedBucket& bucket)
{
    Operation::SP operation(_operationGenerator.generate(bucket.getBucket()));
    if (!operation) {
        return true;
    }
    OperationStarter::Priority operationPriority(
            convertToOperationPriority(bucket.getPriority()));
    return _operationStarter.start(operation, operationPriority);
}

}

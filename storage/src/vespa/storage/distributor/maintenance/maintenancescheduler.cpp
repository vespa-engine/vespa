// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maintenancescheduler.h"
#include "maintenanceoperationgenerator.h"
#include "pending_window_checker.h"
#include <vespa/storage/distributor/operationstarter.h>
#include <vespa/storage/distributor/operations/idealstate/idealstateoperation.h>

#include <vespa/log/log.h>
LOG_SETUP(".storage.distributor.maintenance.maintenance_scheduler");

namespace storage::distributor {

MaintenanceScheduler::MaintenanceScheduler(
        MaintenanceOperationGenerator& operationGenerator,
        BucketPriorityDatabase& priorityDb,
        const PendingWindowChecker& pending_window_checker,
        OperationStarter& operationStarter)
    : _operationGenerator(operationGenerator),
      _priorityDb(priorityDb),
      _pending_window_checker(pending_window_checker),
      _operationStarter(operationStarter),
      _implicitly_clear_priority_on_schedule(false)
{
}

PrioritizedBucket
MaintenanceScheduler::getMostImportantBucket()
{
    auto mostImportant = _priorityDb.begin();
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
    // Bucket activations are so important to do ASAP that we _want_ to block further
    // maintenance scheduling until we're able to schedule the next possible bucket.
    // The inverse is the case for other maintenance operations.
    const bool is_activation = has_bucket_activation_priority(mostImportant);
    if (_implicitly_clear_priority_on_schedule && !is_activation) {
        // If we can't start the operation, move on to the next bucket. Bucket will be
        // re-prioritized when the distributor stripe next scans it.
        clearPriority(mostImportant);
    }
    if (!startOperation(mostImportant)) {
        return WaitTimeMs(1);
    }
    if (!_implicitly_clear_priority_on_schedule || is_activation) {
        clearPriority(mostImportant);
    }
    return WaitTimeMs(0);
}

bool
MaintenanceScheduler::possibleToSchedule(const PrioritizedBucket& bucket,
                                         SchedulingMode currentMode) const
{
    if (!bucket.valid()) {
        return false;
    }
    // If pending window is full nothing of equal or lower priority can be scheduled, so no point in trying.
    if (_implicitly_clear_priority_on_schedule &&
        !_pending_window_checker.may_allow_operation_with_priority(convertToOperationPriority(bucket.getPriority())))
    {
        return false;
    }
    if (currentMode == RECOVERY_SCHEDULING_MODE) {
        return possibleToScheduleInEmergency(bucket);
    } else {
        return true;
    }
}

bool
MaintenanceScheduler::possibleToScheduleInEmergency(
        const PrioritizedBucket& bucket) const
{
    return bucket.moreImportantThan(MaintenancePriority::VERY_HIGH);
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
        return OperationStarter::Priority(30);
    case MaintenancePriority::HIGHEST:
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

bool
MaintenanceScheduler::has_bucket_activation_priority(const PrioritizedBucket& bucket) const noexcept
{
    return (bucket.getPriority() == MaintenancePriority::HIGHEST);
}

}

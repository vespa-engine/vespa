// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blockable_maintenance_job.h"
#include "resource_usage_state.h"
#include "imaintenancejobrunner.h"
#include "document_db_maintenance_config.h"
#include "maintenance_job_token_source.h"
#include "move_operation_limiter.h"

namespace proton {

void
BlockableMaintenanceJob::updateBlocked(const LockGuard &)
{
    _blocked = !_blockReasons.empty();
}

void
BlockableMaintenanceJob::internal_notify_resource_usage(const ResourceUsageState &state)
{
    bool resourcesOK = !state.aboveDiskLimit(_resourceLimitFactor) &&
        !state.aboveMemoryLimit(_resourceLimitFactor) &&
        !state.max_attribute_address_space_state().aboveLimit();
    if (resourcesOK) {
        if (isBlocked(BlockedReason::RESOURCE_LIMITS)) {
            unBlock(BlockedReason::RESOURCE_LIMITS);
        }
    } else {
        setBlocked(BlockedReason::RESOURCE_LIMITS);
    }
}

BlockableMaintenanceJob::BlockableMaintenanceJob(const std::string &name,
                                                 vespalib::duration delay,
                                                 vespalib::duration interval)
    : BlockableMaintenanceJob(name, delay, interval, BlockableMaintenanceJobConfig())
{
}

BlockableMaintenanceJob::BlockableMaintenanceJob(const std::string &name,
                                                 vespalib::duration delay,
                                                 vespalib::duration interval,
                                                 const BlockableMaintenanceJobConfig &config)
    : IBlockableMaintenanceJob(name, delay, interval),
      _mutex(),
      _blockReasons(),
      _blocked(false),
      _runner(nullptr),
      _resourceLimitFactor(config.getResourceLimitFactor()),
      _moveOpsLimiter(std::make_shared<MoveOperationLimiter>(this, config.getMaxOutstandingMoveOps())),
      _token(),
      _token_source()
{
}

BlockableMaintenanceJob::~BlockableMaintenanceJob()
{
    dynamic_cast<MoveOperationLimiter &>(*_moveOpsLimiter).clearJob();
}

bool
BlockableMaintenanceJob::isBlocked(BlockedReason reason)
{
    LockGuard guard(_mutex);
    return (_blockReasons.find(reason) != _blockReasons.end());
}

void
BlockableMaintenanceJob::got_token(std::shared_ptr<MaintenanceJobToken> token, bool sync)
{
    {
        LockGuard guard(_mutex);
        _token = std::move(token);
    }
    if (!sync) {
        unBlock(BlockedReason::JOB_TOKEN);
    }
}

void
BlockableMaintenanceJob::considerRun()
{
    if (_runner && !isBlocked()) {
        _runner->run();
    }
}

void
BlockableMaintenanceJob::setBlocked(BlockedReason reason)
{
    LockGuard guard(_mutex);
    _blockReasons.insert(reason);
    updateBlocked(guard);
}

void
BlockableMaintenanceJob::unBlock(BlockedReason reason)
{
    bool considerRun = false;
    {
        LockGuard guard(_mutex);
        _blockReasons.erase(reason);
        updateBlocked(guard);
        considerRun = !_blocked;
    }
    if (_runner && considerRun) {
        _runner->run();
    }
}

void
BlockableMaintenanceJob::onStop()
{
    LockGuard guard(_mutex);
    _runner = nullptr;
}

bool
BlockableMaintenanceJob::isBlocked() const
{
    LockGuard guard(_mutex);
    return _blocked;
}

}

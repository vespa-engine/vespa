// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blockable_maintenance_job.h"
#include "imaintenancejobrunner.h"

namespace proton {

void
BlockableMaintenanceJob::updateBlocked(const LockGuard &)
{
    _blocked = !_blockReasons.empty();
}

BlockableMaintenanceJob::BlockableMaintenanceJob(const vespalib::string &name,
                                                 double delay,
                                                 double interval)
    : IBlockableMaintenanceJob(name, delay, interval),
      _mutex(),
      _blockReasons(),
      _blocked(false),
      _runner(nullptr)
{
}

BlockableMaintenanceJob::~BlockableMaintenanceJob()
{
}

bool
BlockableMaintenanceJob::isBlocked(BlockedReason reason)
{
    LockGuard guard(_mutex);
    return (_blockReasons.find(reason) != _blockReasons.end());
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
        bool blockedBefore = _blocked;
        _blockReasons.erase(reason);
        updateBlocked(guard);
        considerRun = (!_blocked && blockedBefore);
    }
    if (_runner && considerRun) {
        _runner->run();
    }
}

bool
BlockableMaintenanceJob::isBlocked() const
{
    LockGuard guard(_mutex);
    return _blocked;
}

}

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
    bool blocked = false;
    {
        LockGuard guard(_mutex);
        _blockReasons.erase(reason);
        updateBlocked(guard);
        blocked = _blocked;
    }
    if (_runner && !blocked) {
        _runner->run();
    }
}

}

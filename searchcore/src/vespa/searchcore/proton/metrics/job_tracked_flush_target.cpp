// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.metrics.job_tracked_flush_target");
#include "job_tracked_flush_target.h"
#include "job_tracked_flush_task.h"
#include <memory>

using searchcorespi::IFlushTarget;
using searchcorespi::FlushTask;

namespace proton {

JobTrackedFlushTarget::JobTrackedFlushTarget(const IJobTracker::SP &tracker,
                                             const IFlushTarget::SP &target)
    : IFlushTarget(target->getName(), target->getType(), target->getComponent()),
      _tracker(tracker),
      _target(target)
{
}

FlushTask::UP
JobTrackedFlushTarget::initFlush(SerialNum currentSerial)
{
    _tracker->start();
    FlushTask::UP targetTask = _target->initFlush(currentSerial);
    _tracker->end();
    if (targetTask.get() != nullptr) {
        return FlushTask::UP(new JobTrackedFlushTask(_tracker, std::move(targetTask)));
    }
    return FlushTask::UP();
}

uint64_t
JobTrackedFlushTarget::getApproxBytesToWriteToDisk() const
{
    return _target->getApproxBytesToWriteToDisk();
}

} // namespace proton

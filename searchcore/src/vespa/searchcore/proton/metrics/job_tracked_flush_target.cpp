// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "job_tracked_flush_target.h"
#include "job_tracked_flush_task.h"

using searchcorespi::IFlushTarget;
using searchcorespi::FlushTask;

namespace proton {

JobTrackedFlushTarget::JobTrackedFlushTarget(std::shared_ptr<IJobTracker> tracker,
                                             std::shared_ptr<IFlushTarget> target)
    : IFlushTarget(target->getName(), target->getType(), target->getComponent()),
      _tracker(std::move(tracker)),
      _target(std::move(target))
{
}

JobTrackedFlushTarget::~JobTrackedFlushTarget() = default;

FlushTask::UP
JobTrackedFlushTarget::initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token)
{
    _tracker->start();
    FlushTask::UP targetTask = _target->initFlush(currentSerial, std::move(flush_token));
    _tracker->end();
    if (targetTask) {
        return std::make_unique<JobTrackedFlushTask>(_tracker, std::move(targetTask));
    }
    return FlushTask::UP();
}

uint64_t
JobTrackedFlushTarget::getApproxBytesToWriteToDisk() const
{
    return _target->getApproxBytesToWriteToDisk();
}

} // namespace proton

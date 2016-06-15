// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.metrics.job_tracked_flush_task");
#include "job_tracked_flush_task.h"

using searchcorespi::FlushTask;

namespace proton {

JobTrackedFlushTask::JobTrackedFlushTask(const IJobTracker::SP &tracker,
                                         FlushTask::UP task)
    : _tracker(tracker),
      _task(std::move(task))
{
}

void
JobTrackedFlushTask::run()
{
    _tracker->start();
    _task->run();
    _tracker->end();
}

} // namespace proton

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "job_tracked_flush_task.h"

using searchcorespi::FlushTask;

namespace proton {

JobTrackedFlushTask::JobTrackedFlushTask(std::shared_ptr<IJobTracker> tracker, FlushTask::UP task)
    : _tracker(std::move(tracker)),
      _task(std::move(task))
{
}

JobTrackedFlushTask::~JobTrackedFlushTask() = default;

void
JobTrackedFlushTask::run()
{
    _tracker->start();
    _task->run();
    _tracker->end();
}

}

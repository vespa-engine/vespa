// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_job_tracker.h"
#include <vespa/searchcorespi/flush/flushtask.h>

namespace proton {

/**
 * Class that tracks the start and end of a flush task.
 */
class JobTrackedFlushTask : public searchcorespi::FlushTask
{
private:
    std::shared_ptr<IJobTracker>  _tracker;
    searchcorespi::FlushTask::UP _task;

public:
    JobTrackedFlushTask(std::shared_ptr<IJobTracker> tracker,
                        searchcorespi::FlushTask::UP task);
    JobTrackedFlushTask(const JobTrackedFlushTask &) = delete;
    JobTrackedFlushTask & operator = (const JobTrackedFlushTask &) = delete;
    ~JobTrackedFlushTask() override;

    void run() override;
    search::SerialNum getFlushSerial() const override {
        return _task->getFlushSerial();
    }
};

} // namespace proton


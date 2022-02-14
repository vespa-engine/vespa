// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "job_tracked_maintenance_job.h"

namespace proton {

JobTrackedMaintenanceJob::JobTrackedMaintenanceJob(std::shared_ptr<IJobTracker> tracker,
                                                   IMaintenanceJob::SP job)
    : IMaintenanceJob(job->getName(), job->getDelay(), job->getInterval()),
      _tracker(std::move(tracker)),
      _job(std::move(job)),
      _running(false)
{
}

JobTrackedMaintenanceJob::~JobTrackedMaintenanceJob()
{
    if (_running) {
        _tracker->end();
    }
}

bool
JobTrackedMaintenanceJob::run()
{
    if (!_running) {
        _running = true;
        _tracker->start();
    }
    bool finished = _job->run();
    if (finished) {
        _running = false;
        _tracker->end();
    }
    return finished;
}

} // namespace proton

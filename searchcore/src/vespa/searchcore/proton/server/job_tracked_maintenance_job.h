// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_maintenance_job.h"
#include <vespa/searchcore/proton/metrics/i_job_tracker.h>

namespace proton {

/**
 * Class for tracking the start and end of a maintenance job.
 */
class JobTrackedMaintenanceJob : public IMaintenanceJob
{
private:
    IJobTracker::SP     _tracker;
    IMaintenanceJob::UP _job;
    bool                _running;

public:
    JobTrackedMaintenanceJob(const IJobTracker::SP &tracker, IMaintenanceJob::UP job);
    ~JobTrackedMaintenanceJob() override;

    bool isBlocked() const override { return _job->isBlocked(); }
    IBlockableMaintenanceJob *asBlockable() override { return _job->asBlockable(); }
    void registerRunner(IMaintenanceJobRunner *runner) override {
        _job->registerRunner(runner);
    }
    bool run() override;
};

} // namespace proton


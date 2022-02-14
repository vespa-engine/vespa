// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_maintenance_job.h"
#include <vespa/searchcore/proton/metrics/i_job_tracker.h>

namespace proton {

/**
 * Class for tracking the start and end of a maintenance job.
 */
class JobTrackedMaintenanceJob final : public IMaintenanceJob
{
private:
    std::shared_ptr<IJobTracker>  _tracker;
    IMaintenanceJob::SP           _job;
    bool                          _running;

public:
    JobTrackedMaintenanceJob(std::shared_ptr<IJobTracker> tracker, IMaintenanceJob::SP job);
    JobTrackedMaintenanceJob(const JobTrackedMaintenanceJob &) = delete;
    JobTrackedMaintenanceJob & operator = (const JobTrackedMaintenanceJob &) = delete;
    ~JobTrackedMaintenanceJob() override;

    bool isBlocked() const override { return _job->isBlocked(); }
    IBlockableMaintenanceJob *asBlockable() override { return _job->asBlockable(); }
    void registerRunner(IMaintenanceJobRunner *runner) override {
        _job->registerRunner(runner);
    }
    bool run() override;
    void onStop() override { _job->stop(); }
};

} // namespace proton


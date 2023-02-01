// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maintenance_controller_explorer.h"
#include <vespa/vespalib/data/slime/cursor.h>

using vespalib::slime::Cursor;
using vespalib::slime::Inserter;

namespace proton {

namespace {

void
convertRunningJobsToSlime(const std::vector<MaintenanceJobRunner::SP> &jobs, Cursor &array)
{
    for (const auto &jobRunner : jobs) {
        if (jobRunner->isRunnable()) {
            Cursor &object = array.addObject();
            object.setString("name", jobRunner->getJob().getName());
        }
    }
}

void
convertAllJobsToSlime(const std::vector<MaintenanceJobRunner::SP> &jobs, Cursor &array)
{
    for (const auto &jobRunner : jobs) {
        Cursor &object = array.addObject();
        const IMaintenanceJob &job = jobRunner->getJob();
        object.setString("name", job.getName());
        object.setDouble("delay", vespalib::to_s(job.getDelay()));
        object.setDouble("interval", vespalib::to_s(job.getInterval()));
        object.setBool("blocked", job.isBlocked());
    }
}

}

MaintenanceControllerExplorer::
MaintenanceControllerExplorer(std::vector<MaintenanceJobRunner::SP> jobs)
    : _jobs(std::move(jobs))
{
}
MaintenanceControllerExplorer::~MaintenanceControllerExplorer() = default;

void
MaintenanceControllerExplorer::get_state(const Inserter &inserter, bool full) const
{
    Cursor &object = inserter.insertObject();
    if (full) {
        convertRunningJobsToSlime(_jobs, object.setArray("runningJobs"));
        convertAllJobsToSlime(_jobs, object.setArray("allJobs"));
    }
}

} // namespace proton

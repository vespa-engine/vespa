// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "maintenancejobrunner.h"
#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

/**
 * Class used to explore the state of a maintenance controller and its jobs.
 */
class MaintenanceControllerExplorer : public vespalib::StateExplorer
{
private:
    std::vector<MaintenanceJobRunner::SP> _jobs;

public:
    MaintenanceControllerExplorer(std::vector<MaintenanceJobRunner::SP> jobs);

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

} // namespace proton

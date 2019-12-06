// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_maintenance_job.h"
#include <vespa/searchcore/proton/matching/isessioncachepruner.h>

namespace proton {

/**
 * Job that regularly prunes a session cache.
 */
class PruneSessionCacheJob : public IMaintenanceJob
{
private:
    matching::ISessionCachePruner &_pruner;

public:
    PruneSessionCacheJob(matching::ISessionCachePruner &pruner, vespalib::duration jobInterval);

    bool run() override;
};

} // namespace proton


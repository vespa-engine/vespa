// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_maintenance_job.h"

namespace proton {

class MaintenanceJobToken;

/**
 * Interface for a maintenance job that can be blocked and unblocked due to various external reasons.
 * A blocked job is not executed. When unblocked, the job should be scheduled for execution again.
 */
class IBlockableMaintenanceJob : public IMaintenanceJob {
public:
    enum class BlockedReason {
        RESOURCE_LIMITS = 0,
        FROZEN_BUCKET = 1,
        CLUSTER_STATE = 2,
        OUTSTANDING_OPS = 3,
        DRAIN_OUTSTANDING_OPS = 4,
        JOB_TOKEN = 5
    };

    IBlockableMaintenanceJob(const std::string &name,
                             vespalib::duration delay,
                             vespalib::duration interval)
        : IMaintenanceJob(name, delay, interval)
    {}

    /**
     * Block this job due to the given reason.
     * Should be called from the same executor thread as the one used in IMaintenanceJobRunner.
     */
    virtual void setBlocked(BlockedReason reason) = 0;

    /**
     * Unblock this job for the given reason and consider running the job again if not blocked anymore.
     * Can be called from any thread.
     */
    virtual void unBlock(BlockedReason reason) = 0;

    virtual void got_token(std::shared_ptr<MaintenanceJobToken> token, bool sync) = 0;

    IBlockableMaintenanceJob *asBlockable() override { return this; }
};

}

// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.concurrent.maintenance.JobControlState;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.flags.PermanentFlags;

import java.util.Set;

/**
 * An implementation of {@link JobControlState} that uses a feature flag to control maintenance jobs.
 *
 * @author mpolden
 */
public class JobControlFlags implements JobControlState {

    private final CuratorDb curator;
    private final ListFlag<String> inactiveJobsFlag;

    public JobControlFlags(CuratorDb curator, FlagSource flagSource) {
        this.curator = curator;
        this.inactiveJobsFlag = PermanentFlags.INACTIVE_MAINTENANCE_JOBS.bindTo(flagSource);
    }

    @Override
    public Set<String> readInactiveJobs() {
        return Set.copyOf(inactiveJobsFlag.value());
    }

    @Override
    public Mutex lockMaintenanceJob(String job) {
        return curator.lockMaintenanceJob(job);
    }

}

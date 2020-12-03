package com.yahoo.vespa.hosted.provision.persistence;

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

    private final CuratorDatabaseClient curator;
    private final ListFlag<String> inactiveJobsFlag;

    public JobControlFlags(CuratorDatabaseClient curator, FlagSource flagSource) {
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

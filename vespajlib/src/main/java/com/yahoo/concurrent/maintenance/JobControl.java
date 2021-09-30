// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import com.yahoo.transaction.Mutex;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Provides status over running maintenance jobs.
 *
 * This is multi-thread safe.
 *
 * @author bratseth
 */
public class JobControl {

    /** This is not persisted as all nodes start all jobs */
    private final Map<String, Maintainer> startedJobs = new ConcurrentSkipListMap<>();

    /** Used for managing shared persistent state, to make changes take effect on all nodes */
    private final JobControlState state;

    public JobControl(JobControlState state) {
        this.state = Objects.requireNonNull(state);
    }

    /** Notifies this that a job was started */
    public void started(String jobSimpleClassName, Maintainer maintainer) {
        startedJobs.put(jobSimpleClassName, maintainer);
    }

    /**
     * Returns a snapshot of the set of jobs started on this system (whether deactivated or not).
     * Each job is represented by its simple (omitting package) class name.
     */
    public Set<String> jobs() { return Collections.unmodifiableSet(startedJobs.keySet()); }

    /** Returns a snapshot containing the currently inactive jobs in this */
    public Set<String> inactiveJobs() { return state.readInactiveJobs(); }

    /** Returns true if this job is not currently deactivated */
    public boolean isActive(String jobSimpleClassName) {
        return  ! inactiveJobs().contains(jobSimpleClassName);
    }

    /** Run given job (inactive or not) immediately */
    public void run(String jobSimpleClassName) {
        var job = startedJobs.get(jobSimpleClassName);
        if (job == null) throw new IllegalArgumentException("No such job '" + jobSimpleClassName + "'");
        job.lockAndMaintain(true);
    }

    /** Acquire lock for running given job */
    public Mutex lockJob(String jobSimpleClassName) {
        return state.lockMaintenanceJob(jobSimpleClassName);
    }

}

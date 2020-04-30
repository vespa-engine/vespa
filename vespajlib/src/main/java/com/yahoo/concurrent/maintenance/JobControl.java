// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import com.yahoo.transaction.Mutex;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Provides status and control over running maintenance jobs.
 *
 * This is multi-thread safe.
 *
 * @author bratseth
 */
public class JobControl {

    /** This is not persisted as all nodes start all jobs */
    private final Map<String, Maintainer> startedJobs = new ConcurrentSkipListMap<>();

    /** Used to store deactivation in a persistent shared database to make changes take effect on all nodes */
    private final Db db;

    public JobControl(Db db) {
        this.db = db;
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
    public Set<String> inactiveJobs() { return db.readInactiveJobs(); }

    /** Returns true if this job is not currently deactivated */
    public boolean isActive(String jobSimpleClassName) {
        return  ! inactiveJobs().contains(jobSimpleClassName);
    }

    /** Set a job active or inactive */
    public void setActive(String jobSimpleClassName, boolean active) {
        try (var lock = db.lockInactiveJobs()) {
            Set<String> inactiveJobs = db.readInactiveJobs();
            if (active)
                inactiveJobs.remove(jobSimpleClassName);
            else
                inactiveJobs.add(jobSimpleClassName);
            db.writeInactiveJobs(inactiveJobs);
        }
    }

    /** Run given job (inactive or not) immediately */
    public void run(String jobSimpleClassName) {
        var job = startedJobs.get(jobSimpleClassName);
        if (job == null) throw new IllegalArgumentException("No such job '" + jobSimpleClassName + "'");
        job.lockAndMaintain();
    }

    /** Acquire lock for running given job */
    public Mutex lockJob(String jobSimpleClassName) {
        return db.lockMaintenanceJob(jobSimpleClassName);
    }

    /** The database used for managing job state and synchronization */
    public interface Db {

        /** Returns the set of jobs that are temporarily inactive */
        Set<String> readInactiveJobs();

        /** Make given jobs as inactive */
        void writeInactiveJobs(Set<String> inactiveJobs);

        /** Acquire lock for changing jobs */
        Mutex lockInactiveJobs();

        /** Acquire lock for running given job */
        Mutex lockMaintenanceJob(String job);

    }

}

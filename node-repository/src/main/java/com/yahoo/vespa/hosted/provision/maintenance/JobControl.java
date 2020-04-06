// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

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

    /** This is not stored in ZooKeeper as all nodes start all jobs */
    private final Map<String, Maintainer> startedJobs = new ConcurrentSkipListMap<>();

    /** Used to store deactivation in ZooKeeper to make changes take effect on all nodes */
    private final CuratorDatabaseClient db;

    public JobControl(CuratorDatabaseClient db) {
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
        try (Lock lock = db.lockInactiveJobs()) {
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
        job.runWithLock();
    }
    
}

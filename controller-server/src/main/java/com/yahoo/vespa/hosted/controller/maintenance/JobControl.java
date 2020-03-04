// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Provides status and control over running maintenance jobs.
 * This is multithread safe.
 * 
 * Job deactivation is stored in zookeeper.
 * 
 * @author bratseth
 */
public class JobControl {
    
    private final CuratorDb curator;

    /** This is not stored in ZooKeeper as all nodes start all jobs */
    private final Set<String> startedJobs = new ConcurrentSkipListSet<>();

    /** Create a job control instance which persists activation changes to the default directory */
    public JobControl(CuratorDb curator) {
        this.curator = curator;
    }
    
    public CuratorDb curator() { return curator; }
    
    /** Notifies this that a job was started */
    public void started(String jobSimpleClassName) {
        startedJobs.add(jobSimpleClassName);
    }

    /**
     * Returns a snapshot of the set of jobs started on this system (whether deactivated or not).
     * Each job is represented by its simple (omitting package) class name.
     */
    public Set<String> jobs() { return new LinkedHashSet<>(startedJobs); }

    /** Returns an unmodifiable set containing the currently inactive jobs in this */
    public Set<String> inactiveJobs() { return curator.readInactiveJobs(); }
    
    /** Returns true if this job is not currently deactivated */
    public boolean isActive(String jobSimpleClassName) {
        return  ! inactiveJobs().contains(jobSimpleClassName);
    }

    /** Set a job active or inactive */
    public void setActive(String jobSimpleClassName, boolean active) {
        try (Lock lock = curator.lockInactiveJobs()) {
            Set<String> inactiveJobs = curator.readInactiveJobs();
            if (active)
                inactiveJobs.remove(jobSimpleClassName);
            else
                inactiveJobs.add(jobSimpleClassName);
            curator.writeInactiveJobs(inactiveJobs);
        }
    }

}

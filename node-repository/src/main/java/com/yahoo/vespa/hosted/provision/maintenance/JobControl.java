package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;
import com.yahoo.vespa.hosted.provision.persistence.CuratorMutex;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Provides status and control over running maintenance jobs.
 * This is multithread safe.
 * 
 * @author bratseth
 */
public class JobControl {

    /** This is not stored in ZooKeeper as all nodes start all jobs */
    private Set<String> startedJobs = new ConcurrentSkipListSet<>();

    /** Used to store deactivation in ZooKeeper to make changes take effect on all nodes */
    private final CuratorDatabaseClient db;
    
    public JobControl(CuratorDatabaseClient db) {
        this.db = db;
    }
    
    /** Notifies this that a job was started */
    public void started(String jobSimpleClassName) {
        startedJobs.add(jobSimpleClassName);
    }

    /**
     * Returns a snapshot of the set of jobs started on this system (whether deactivated or not).
     * Each job is represented by its simple (omitting package) class name.
     */
    public Set<String> jobs() { return new HashSet<>(startedJobs); }

    /** Returns a snapshot containing the currently inactive jobs in this */
    public Set<String> inactiveJobs() { return db.readInactiveJobs(); }
    
    /** Returns true if this job is not currently deactivated */
    public boolean isActive(String jobSimpleClassName) {
        return  ! db.readInactiveJobs().contains(jobSimpleClassName);
    }

    /** Set a job active or inactive */
    public void setActive(String jobSimpleClassName, boolean active) {
        try (CuratorMutex lock = db.lockInactiveJobs()) {
            Set<String> inactiveJobs = db.readInactiveJobs();
            if (active)
                inactiveJobs.remove(jobSimpleClassName);
            else
                inactiveJobs.add(jobSimpleClassName);
            db.writeInactiveJobs(inactiveJobs);
        }
    }
    
}

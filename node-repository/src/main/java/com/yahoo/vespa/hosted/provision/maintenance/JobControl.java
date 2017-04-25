package com.yahoo.vespa.hosted.provision.maintenance;

import java.util.Collections;
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
    
    private Set<String> startedJobs = new ConcurrentSkipListSet<>();
    private Set<String> deactivatedJobs = new ConcurrentSkipListSet<>();

    /**
     * Returns a snapshot of the set of jobs started on this system (whether deactivated or not).
     * Each job is represented by its simple (omitting package) class name.
     */
    public Set<String> jobs() { return new HashSet<>(startedJobs); }
    
    /** Returns true if this job is not currently deactivated */
    public boolean isActive(String jobSimpleClassName) {
        return ! deactivatedJobs.contains(jobSimpleClassName);
    }

    /** Set a job active or inactive */
    public void setActive(String jobSimpleClassName, boolean active) {
        if (active)
            deactivatedJobs.remove(jobSimpleClassName);
        else
            deactivatedJobs.add(jobSimpleClassName);
    }
    
}

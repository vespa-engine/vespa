// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import com.yahoo.transaction.Mutex;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author mpolden
 */
class JobControlStateMock implements JobControlState {

    private final Set<String> inactiveJobs = new HashSet<>();

    @Override
    public Set<String> readInactiveJobs() {
        return Collections.unmodifiableSet(inactiveJobs);
    }

    @Override
    public Mutex lockMaintenanceJob(String job) {
        return () -> {};
    }

    public void setActive(String job, boolean active) {
        if (active) {
            inactiveJobs.remove(job);
        } else {
            inactiveJobs.add(job);
        }
    }

}

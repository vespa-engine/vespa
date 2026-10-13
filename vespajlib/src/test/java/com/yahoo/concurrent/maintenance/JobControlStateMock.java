// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.transaction.Mutex;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author mpolden
 */
class JobControlStateMock implements JobControlState {

    private final Set<String> inactiveJobs = new HashSet<>();
    private boolean lockTimesOut = false;
    private RuntimeException releaseException = null;

    @Override
    public Set<String> readInactiveJobs() {
        return Collections.unmodifiableSet(inactiveJobs);
    }

    @Override
    public Mutex lockMaintenanceJob(String job) {
        if (lockTimesOut) throw new UncheckedTimeoutException("Timed out locking " + job);
        return () -> { if (releaseException != null) throw releaseException; };
    }

    public void setLockTimesOut(boolean lockTimesOut) {
        this.lockTimesOut = lockTimesOut;
    }

    public void setReleaseThrows(RuntimeException releaseException) {
        this.releaseException = releaseException;
    }

    public void setActive(String job, boolean active) {
        if (active) {
            inactiveJobs.remove(job);
        } else {
            inactiveJobs.add(job);
        }
    }

}

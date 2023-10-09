// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import com.yahoo.transaction.Mutex;

import java.util.Set;

/**
 * Interface for managing job state and synchronization
 *
 * @author mpolden
 */
public interface JobControlState {

    /** Returns the set of jobs that are temporarily inactive */
    Set<String> readInactiveJobs();

    /** Acquire lock for running given job */
    Mutex lockMaintenanceJob(String job);

}

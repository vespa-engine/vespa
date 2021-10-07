// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;

/**
 * @author mortent
 */
public class RetriggerEntry {
    private final JobId jobId;
    private final long requiredRun;

    public RetriggerEntry(JobId jobId, long requiredRun) {
        this.jobId = jobId;
        this.requiredRun = requiredRun;
    }

    public JobId jobId() {
        return jobId;
    }

    public long requiredRun() {
        return requiredRun;
    }

}

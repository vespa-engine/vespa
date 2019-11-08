// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.ApplicationId;

import java.util.Objects;

/**
 * Immutable ID of a deployment job.
 *
 * @author jonmv
 */
public class RunId {

    private final JobId jobId;
    private final TesterId tester;
    private final long number;

    public RunId(ApplicationId application, JobType type, long number) {
        this.jobId = new JobId(application, type);
        this.tester = TesterId.of(application);
        if (number <= 0) throw new IllegalArgumentException("Build number must be a positive integer!");
        this.number = number;
    }

    public JobId job() { return jobId; }
    public ApplicationId application() { return jobId.application(); }
    public TesterId tester() { return tester; }
    public JobType type() { return jobId.type(); }
    public long number() { return number; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RunId runId = (RunId) o;
        return number == runId.number &&
               jobId.equals(runId.jobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, number);
    }

    @Override
    public String toString() {
        return "run " + number + " of " + type().jobName() + " for " + application();
    }

}

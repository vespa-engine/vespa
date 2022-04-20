// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.ApplicationId;

import java.util.Objects;

/**
 * Immutable ID of a job that may be run.
 *
 * @author jonmv
 */
public class JobId {

    private final ApplicationId application;
    private final JobType type;

    public JobId(ApplicationId application, JobType type) {
        this.application = Objects.requireNonNull(application, "ApplicationId cannot be null!");
        this.type = Objects.requireNonNull(type, "JobType cannot be null!");
    }

    public ApplicationId application() { return application; }
    public JobType type() { return type; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobId jobId = (JobId) o;
        return application.equals(jobId.application) &&
               type.equals(jobId.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(application, type);
    }

    @Override
    public String toString() {
        return  type.jobName() + " for " + application;
    }

}

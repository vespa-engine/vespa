package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;

import java.util.Objects;

/**
 * Immutable ID of a job run by an {@link InternalBuildService}.
 *
 * @author jonmv
 */
public class JobId {

    private final ApplicationId application;
    private final String type;
    private final long number;

    public JobId(ApplicationId application, String type, long number) {
        this.application = Objects.requireNonNull(application, "ApplicationId cannot be null!");
        this.type = Objects.requireNonNull(type, "JobType cannot be null!");
        if (number <= 0) throw new IllegalArgumentException("Build number must be a positive long integer!");
        this.number = number;
    }

    public ApplicationId application() { return application; }
    public String type() { return type; }
    public long number() { return number; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof JobId)) return false;

        JobId id = (JobId) o;

        if (number != id.number) return false;
        if ( ! application.equals(id.application)) return false;
        return type == id.type;
    }

    @Override
    public int hashCode() {
        int result = application.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + (int) (number ^ (number >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Run " + number + " of " + type + " for " + application;
    }

}

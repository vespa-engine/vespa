// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.ApplicationId;

import java.util.Objects;

/**
 * Immutable ID of a job run by a {@link com.yahoo.vespa.hosted.controller.api.integration.BuildService}.
 *
 * @author jonmv
 */
public class RunId {

    private final ApplicationId application;
    private final JobType type;
    private final long number;

    public RunId(ApplicationId application, JobType type, long number) {
        this.application = Objects.requireNonNull(application, "ApplicationId cannot be null!");
        this.type = Objects.requireNonNull(type, "JobType cannot be null!");
        if (number <= 0) throw new IllegalArgumentException("Build number must be a positive integer!");
        this.number = number;
    }

    public ApplicationId application() { return application; }
    public JobType type() { return type; }
    public long number() { return number; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof RunId)) return false;

        RunId id = (RunId) o;

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
        return "run " + number + " of " + type + " for " + application;
    }

}

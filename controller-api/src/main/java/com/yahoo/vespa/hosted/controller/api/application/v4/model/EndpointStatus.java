// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represent the routing status for all endpoints of a deployment.
 *
 * @author smorgrav
 */
public class EndpointStatus {

    private final String agent;
    private final Status status;
    private final Instant changedAt;

    public EndpointStatus(Status status, String agent, Instant changedAt) {
        this.status = Objects.requireNonNull(status);
        this.agent = Objects.requireNonNull(agent);
        this.changedAt = Objects.requireNonNull(changedAt);
    }

    /** Returns the agent responsible setting this status */
    public String agent() {
        return agent;
    }

    /** Returns the current status */
    public Status status() {
        return status;
    }

    /** Returns when this was last changed */
    public Instant changedAt() {
        return changedAt;
    }

    public enum Status {
        in,
        out,
        unknown;
    }

}

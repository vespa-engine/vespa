// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents the global routing status of a {@link RoutingPolicy} or {@link ZoneRoutingPolicy}. This contains the
 * time global routing status was last changed and who changed it.
 *
 * This is immutable.
 *
 * @author mpolden
 */
public class GlobalRouting {

    public static final GlobalRouting DEFAULT_STATUS = new GlobalRouting(Status.in, Agent.system, Instant.EPOCH);

    private final Status status;
    private final Agent agent;
    private final Instant changedAt;

    /** DO NOT USE. Public for serialization purposes */
    public GlobalRouting(Status status, Agent agent, Instant changedAt) {
        this.status = Objects.requireNonNull(status, "status must be non-null");
        this.agent = Objects.requireNonNull(agent, "agent must be non-null");
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt must be non-null");
    }

    /** The current status of this */
    public Status status() {
        return status;
    }

    /** The agent who last changed this */
    public Agent agent() {
        return agent;
    }

    /** The time this was last changed */
    public Instant changedAt() {
        return changedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GlobalRouting that = (GlobalRouting) o;
        return status == that.status &&
               agent == that.agent &&
               changedAt.equals(that.changedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, agent, changedAt);
    }

    @Override
    public String toString() {
        return "status " + status + ", changed by " + agent + " @ " + changedAt;
    }

    public static GlobalRouting status(Status status, Agent agent, Instant instant) {
        return new GlobalRouting(status, agent, instant);
    }

    // Used in serialization. Do not change.
    public enum Status {
        /** Status is determined by health checks **/
        in,

        /** Status is explicitly set to out */
        out,
    }

    /** Agents that can change the state of global routing */
    public enum Agent {
        operator,
        tenant,
        system,
    }

}

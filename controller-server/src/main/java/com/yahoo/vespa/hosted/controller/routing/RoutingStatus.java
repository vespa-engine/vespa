// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents the routing status of a {@link RoutingPolicy} or {@link ZoneRoutingPolicy}.
 *
 * This describes which agent last changed the routing status and at which time.
 *
 * This is immutable.
 *
 * @author mpolden
 */
public record RoutingStatus(Value value, Agent agent, Instant changedAt) {

    public static final RoutingStatus DEFAULT = new RoutingStatus(Value.in, Agent.system, Instant.EPOCH);

    /** DO NOT USE. Public for serialization purposes */
    public RoutingStatus {
        Objects.requireNonNull(value, "value must be non-null");
        Objects.requireNonNull(agent, "agent must be non-null");
        Objects.requireNonNull(changedAt, "changedAt must be non-null");
    }

    /**
     * The wanted value of this. The system will try to set this value, but there are constraints that may lead to
     * the effective value not matching this. See {@link RoutingPolicies}.
     */
    public Value value() {
        return value;
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
    public String toString() {
        return "status " + value + ", changed by " + agent + " @ " + changedAt;
    }

    public static RoutingStatus create(Value value, Agent agent, Instant instant) {
        return new RoutingStatus(value, agent, instant);
    }

    // Used in serialization. Do not change.
    public enum Value {
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
        unknown, // For compatibility old values from /routing/v1 on config server, which may contain a specific username.
    }

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.vcmr;

import java.time.Instant;
import java.util.Objects;

/**
 * @author olaa
 *
 * Contains planned/current action for a host impacted by a change request
 */
public class HostAction {

    private final String hostname;
    private final State state;
    private final Instant lastUpdated;

    public HostAction(String hostname, State state, Instant lastUpdated) {
        this.hostname = hostname;
        this.state = state;
        this.lastUpdated = lastUpdated;
    }

    public String getHostname() {
        return hostname;
    }

    public State getState() {
        return state;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public HostAction withState(State state) {
        return new HostAction(hostname, state, this.state == state ? lastUpdated : Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostAction that = (HostAction) o;
        return Objects.equals(hostname, that.hostname) &&
                state == that.state &&
                Objects.equals(lastUpdated, that.lastUpdated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, state, lastUpdated);
    }

    @Override
    public String toString() {
        return "HostAction{" +
                "hostname='" + hostname + '\'' +
                ", state=" + state +
                ", lastUpdated=" + lastUpdated +
                '}';
    }

    public enum State {
        REQUIRES_OPERATOR_ACTION,
        PENDING_RETIREMENT,
        OUT_OF_SYNC,
        NONE,
        RETIRING,
        RETIRED,
        COMPLETE
    }
}

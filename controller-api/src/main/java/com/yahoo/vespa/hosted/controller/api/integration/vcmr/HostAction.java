// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.vcmr;

import java.time.Instant;
import java.util.Objects;

/**
 * @author olaa
 *
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

    public enum State {
        REQUIRES_OPERATOR_ACTION,
        PENDING_RETIREMENT,
        RETIRING,
        RETIRED,
        COMPLETE
    }
}

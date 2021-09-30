// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.concurrent.Immutable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * @author hakonhall
 */
@Immutable
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClusterControllerStateRequest {

    @JsonProperty("state")
    public final Map<String, State> state;

    @JsonProperty("condition")
    public final Condition condition;

    @JsonProperty("probe")
    public final Boolean probe;

    public ClusterControllerStateRequest(State currentState, Condition condition, Boolean probe) {
        Map<String, State> state = Collections.singletonMap("user", currentState);
        this.state = Collections.unmodifiableMap(state);
        this.condition = condition;
        this.probe = probe;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterControllerStateRequest that = (ClusterControllerStateRequest) o;
        return Objects.equals(state, that.state) &&
                condition == that.condition &&
                Objects.equals(probe, that.probe);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, condition, probe);
    }

    @Override
    public String toString() {
        return "NodeStateRequest {"
                + " condition=" + condition
                + " state=" + state
                + " }";
    }

    public static class State {
        @JsonProperty("state")
        public final ClusterControllerNodeState state;

        /**
         * The reason the client is making the request to set the node state.
         * Useful for logging in the Cluster Controller.
         */
        @JsonProperty("reason")
        public final String reason;

        public State(ClusterControllerNodeState state, String reason) {
            this.state = state;
            this.reason = reason;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof State)) {
                return false;
            }

            State that = (State) object;
            return this.state.equals(that.state) &&
                    this.reason.equals(that.reason);
        }

        @Override
        public int hashCode() {
            int hash = 1;
            hash = 17 * hash + state.hashCode();
            hash = 13 * hash + reason.hashCode();
            return hash;
        }

        @Override
        public String toString() {
            return "reason: " + reason + ", state: " + state;
        }
    }

    public enum Condition {
        FORCE, SAFE;
    }

}

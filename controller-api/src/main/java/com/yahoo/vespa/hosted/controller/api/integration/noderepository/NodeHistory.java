// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Wire class for node-repository representation of the history of a node
 *
 * @author smorgrav
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeHistory {

    @JsonProperty("at")
    public Long at;
    @JsonProperty("agent")
    public String agent;
    @JsonProperty("event")
    public String event;

    public Long getAt() {
        return at;
    }

    public String getAgent() {
        return agent;
    }

    public String getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return "NodeHistory{" +
               "at=" + (at == null ? "null" : Instant.ofEpochSecond(at)) +
               ", agent='" + agent + '\'' +
               ", event='" + event + '\'' +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeHistory that = (NodeHistory) o;
        return Objects.equals(at, that.at) &&
               Objects.equals(agent, that.agent) &&
               Objects.equals(event, that.event);
    }

    @Override
    public int hashCode() {
        return Objects.hash(at, agent, event);
    }
}

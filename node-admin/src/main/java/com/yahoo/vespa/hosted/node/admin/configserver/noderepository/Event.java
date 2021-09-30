// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import java.time.Instant;
import java.util.Objects;

/**
 * @author freva
 */
public class Event {
    private final String agent;
    private final String type;
    private final Instant at;

    public Event(String agent, String type, Instant at) {
        this.agent = Objects.requireNonNull(agent);
        this.type = Objects.requireNonNull(type);
        this.at = Objects.requireNonNull(at);
    }

    public String agent() {
        return agent;
    }

    public String type() {
        return type;
    }

    public Instant at() {
        return at;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event1 = (Event) o;
        return agent.equals(event1.agent) && type.equals(event1.type) && at.equals(event1.at);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agent, type, at);
    }

    @Override
    public String toString() {
        return "Event{" +
                "agent='" + agent + '\'' +
                ", type='" + type + '\'' +
                ", at=" + at +
                '}';
    }
}

// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.collect.ImmutableMap;
import com.yahoo.vespa.hosted.provision.Node;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable record of the last event of each type happening to this node.
 * Note that the history cannot be used to find the nodes current state - it will have a record of some
 * event happening in the past even if that event is later undone.
 *
 * @author bratseth
 */
public class History {

    private final ImmutableMap<Event.Type, Event> events;

    public History(Collection<Event> events) {
        this(toImmutableMap(events));
    }

    private History(ImmutableMap<Event.Type, Event> events) {
        this.events = events;
    }

    private static ImmutableMap<Event.Type, Event> toImmutableMap(Collection<Event> events) {
        ImmutableMap.Builder<Event.Type, Event> builder = new ImmutableMap.Builder<>();
        for (Event event : events)
            builder.put(event.type(), event);
        return builder.build();
    }

    /** Returns this event if it is present in this history */
    public Optional<Event> event(Event.Type type) { return Optional.ofNullable(events.get(type)); }

    public Collection<Event> events() { return events.values(); }

    /** Returns a copy of this history with the given event added */
    public History record(Event event) {
        ImmutableMap.Builder<Event.Type, Event> builder = builderWithout(event.type());
        builder.put(event.type(), event);
        return new History(builder.build());
    }

    /** Returns a copy of this history with the given event type removed (or an identical if it was not present) */
    public History clear(Event.Type type) {
        return new History(builderWithout(type).build());
    }

    private ImmutableMap.Builder<Event.Type, Event> builderWithout(Event.Type type) {
        ImmutableMap.Builder<Event.Type, Event> builder = new ImmutableMap.Builder<>();
        for (Event event : events.values())
            if (event.type() != type)
                builder.put(event.type(), event);
        return builder;
    }

    /** Returns a copy of this history with a record of this state transition added, if applicable */
    public History recordStateTransition(Node.State from, Node.State to, Instant at) {
        if (from == to) return this;
        switch (to) {
            case ready:    return record(new Event(Event.Type.readied, at));
            case active:   return record(new Event(Event.Type.activated, at));
            case inactive: return record(new Event(Event.Type.deactivated, at));
            case reserved: return record(new Event(Event.Type.reserved, at));
            case failed:   return record(new Event(Event.Type.failed, at));
            case dirty:    return record(new Event(Event.Type.deallocated, at));
            default:       return this;
        }
    }

    /** Returns the empty history */
    public static History empty() { return new History(Collections.emptyList()); }

    /** An event which may happen to a node */
    public static class Event {

        private final Instant at;
        private final Event.Type type;

        public Event(Event.Type type, Instant at) {
            this.type = type;
            this.at = at;
        }

        /** Returns the type of event */
        public Event.Type type() { return type; }

        /** Returns the instant this even took place */
        public Instant at() { return at; }

        public enum Type { readied, reserved, activated, retired, deactivated, failed, deallocated, down }

        @Override
        public String toString() { return type + " event at " + at; }

    }

    /** A retired event includes additional information about the causing agent. */
    public static class RetiredEvent extends Event {

        private final RetiredEvent.Agent agent;

        public RetiredEvent(Instant at, RetiredEvent.Agent agent) {
            super(Type.retired, at);
            this.agent = agent;
        }

        /** Returns the agent which caused retirement */
        public RetiredEvent.Agent agent() { return agent; }

        public enum Agent { system, application }

    }

}

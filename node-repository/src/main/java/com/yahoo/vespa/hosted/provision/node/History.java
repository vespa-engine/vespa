// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.vespa.hosted.provision.Node;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An immutable list of events happening to this node, in chronological order.
 *
 * Note that the history cannot be used to find the nodes current state - it will have a record of some
 * event happening in the past even if that event is later undone.
 *
 * @author bratseth
 */
public class History {

    /** The maximum number of events to keep for a node */
    private static final int MAX_SIZE = 15;

    private final List<Event> events;

    public History(List<Event> events) {
        this(events, MAX_SIZE);
    }

    History(List<Event> events, int maxSize) {
        this.events = Objects.requireNonNull(events, "events must be non-null")
                             .stream()
                             .sorted(Comparator.comparing(Event::at))
                             .skip(Math.max(events.size() - maxSize, 0))
                             .collect(Collectors.toUnmodifiableList());
    }

    /** Returns the last event of given type, if it is present in this history */
    public Optional<Event> lastEvent(Event.Type type) {
        return events.stream().filter(event -> event.type() == type).max(Comparator.comparing(Event::at));
    }

    /** Returns true if the last event of this type is registered in this history at the given time */
    public boolean hasLastEventAt(Instant time, Event.Type type) {
        return lastEvent(type).map(event -> event.at().equals(time))
                              .orElse(false);
    }

    /** Returns true if the last event of this type is registered after the given time */
    public boolean hasLastEventAfter(Instant time, Event.Type type) {
        return lastEvent(type).map(event -> event.at().isAfter(time))
                              .orElse(false);
    }

    /** Returns true if the last event of this type is registered before the given time */
    public boolean hasLastEventBefore(Instant time, Event.Type type) {
        return lastEvent(type).map(event -> event.at().isBefore(time))
                              .orElse(false);
    }

    public List<Event> asList() {
        return events;
    }

    /** Returns a copy of this history with the given event added */
    public History with(Event event) {
        List<Event> copy = new ArrayList<>(events);
        copy.add(event);
        return new History(copy);
    }

    /** Returns a copy of this history with a record of this state transition added, if applicable */
    public History recordStateTransition(Node.State from, Node.State to, Agent agent, Instant at) {
        // If the event is a re-reservation, allow the new event to overwrite the older one.
        if (from == to && from != Node.State.reserved) return this;
        switch (to) {
            case provisioned:   return this.with(new Event(Event.Type.provisioned, agent, at));
            case deprovisioned: return this.with(new Event(Event.Type.deprovisioned, agent, at));
            case ready:         return this.withoutApplicationEvents().with(new Event(Event.Type.readied, agent, at));
            case active:        return this.with(new Event(Event.Type.activated, agent, at));
            case inactive:      return this.with(new Event(Event.Type.deactivated, agent, at));
            case reserved: {
                History history = this;
                if (!events.isEmpty() && events.get(events.size() - 1).type() == Event.Type.reserved) {
                    // Avoid repeating reserved event
                    history = new History(events.subList(0, events.size() - 1));
                }
                return history.with(new Event(Event.Type.reserved, agent, at));
            }
            case failed:        return this.with(new Event(Event.Type.failed, agent, at));
            case dirty:         return this.with(new Event(Event.Type.deallocated, agent, at));
            case parked:        return this.with(new Event(Event.Type.parked, agent, at));
            case breakfixed:    return this.with(new Event(Event.Type.breakfixed, agent, at));
            default:            return this;
        }
    }
    
    /** 
     * Events can be application or node level. 
     * This returns a copy of this history with all application level events removed. 
     */
    private History withoutApplicationEvents() {
        return new History(asList().stream().filter(e -> ! e.type().isApplicationLevel()).collect(Collectors.toList()));
    }

    /** Returns the empty history */
    public static History empty() { return new History(List.of()); }

    @Override
    public String toString() {
        if (events.isEmpty()) return "history: (empty)";
        StringBuilder b = new StringBuilder("history: ");
        for (Event e : events)
            b.append(e).append(", ");
         b.setLength(b.length() - 2); // remove last comma
        return b.toString();
    }

    /** An event which may happen to a node */
    public static class Event {

        private final Instant at;
        private final Agent agent;
        private final Type type;

        public Event(Event.Type type, Agent agent, Instant at) {
            this.type = type;
            this.agent = agent;
            this.at = at;
        }

        public enum Type { 
            // State changes
            activated,
            breakfixed(false),
            deactivated,
            deallocated,
            deprovisioned(false),
            failed(false),
            parked,
            provisioned(false),
            readied,
            reserved,

            /** The node was scheduled for retirement (hard) */
            wantToRetire(false),
            /** The node was scheduled for retirement (soft) */
            preferToRetire(false),
            /** This node was scheduled for failing */
            wantToFail,
            /** The active node was retired */
            retired,
            /** The active node went down according to the service monitor */
            down,
            /** The active node came up according to the service monitor */
            up,
            /** The node made a config request, indicating it is live */
            requested,
            /** The node resources/flavor were changed */
            resized(false),
            /** The node was rebooted */
            rebooted(false),
            /** The node upgraded its OS (implies a reboot) */
            osUpgraded(false),
            /** The node verified its firmware (whether this resulted in a reboot depends on the node model) */
            firmwareVerified(false);

            private final boolean applicationLevel;

            /** Creates an application level event */
            Type() {
                this.applicationLevel = true;
            }

            Type(boolean applicationLevel) {
                this.applicationLevel = applicationLevel;
            }

            /** Returns true if this is an application level event and false it it is a node level event */
            public boolean isApplicationLevel() { return applicationLevel; }
        }

        /** Returns the type of event */
        public Event.Type type() { return type; }

        /** Returns the agent causing this event */
        public Agent agent() { return agent; }

        /** Returns the instant this even took place */
        public Instant at() { return at; }

        @Override
        public String toString() { return "'" + type + "' event at " + at + " by " + agent; }

    }

}

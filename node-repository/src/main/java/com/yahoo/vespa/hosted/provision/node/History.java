// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.collect.ImmutableMap;
import com.yahoo.vespa.hosted.provision.Node;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An immutable record of the last event of each type happening to this node, and a chronological log of the events.
 *
 * Note that the history cannot be used to find the nodes current state - it will have a record of some
 * event happening in the past even if that event is later undone.
 *
 * @author bratseth
 */
public class History {

    private static final int MAX_LOG_SIZE = 10;

    private final ImmutableMap<Event.Type, Event> events;
    private final List<Event> log;
    private final int maxLogSize;

    public History(Collection<Event> events, List<Event> log) {
        this(toImmutableMap(events), log, MAX_LOG_SIZE);
    }

    History(ImmutableMap<Event.Type, Event> events, List<Event> log, int maxLogSize) {
        this.events = events;
        this.log = Objects.requireNonNull(log, "log must be non-null")
                          .stream()
                          .sorted(Comparator.comparing(Event::at))
                          .skip(Math.max(log.size() - maxLogSize, 0))
                          .toList();
        this.maxLogSize = maxLogSize;
    }

    private static ImmutableMap<Event.Type, Event> toImmutableMap(Collection<Event> events) {
        ImmutableMap.Builder<Event.Type, Event> builder = new ImmutableMap.Builder<>();
        for (Event event : events)
            builder.put(event.type(), event);
        return builder.build();
    }

    /** Returns the age of this node as best as we can determine: The time since the first event registered for it */
    public Duration age(Instant now) {
        Instant oldestEventTime = events.values().stream().map(Event::at).sorted().findFirst().orElse(now);
        return Duration.between(oldestEventTime, now);
    }

    /** Returns the last event of given type, if it is present in this history */
    public Optional<Event> event(Event.Type type) { return Optional.ofNullable(events.get(type)); }

    /** Returns true if a given event is registered in this history at the given time */
    public boolean hasEventAt(Event.Type type, Instant time) {
        return event(type)
                       .map(event -> event.at().equals(time))
                       .orElse(false);
    }

    /** Returns true if a given event is registered in this history after the given time */
    public boolean hasEventAfter(Event.Type type, Instant time) {
        return event(type)
                .map(event -> event.at().isAfter(time))
                .orElse(false);
    }

    /** Returns true if a given event is registered in this history before the given time */
    public boolean hasEventBefore(Event.Type type, Instant time) {
        return event(type)
                .map(event -> event.at().isBefore(time))
                .orElse(false);
    }

    /** Returns the last event of each type in this history */
    public Collection<Event> events() { return events.values(); }

    /**
     * Returns the events in this history, in chronological order. Compared to {@link #events()}, this holds all events
     * as they occurred, up to log size limit
     */
    public List<Event> log() { return log; }

    /** Returns a copy of this history with the given event added */
    public History with(Event event) {
        ImmutableMap.Builder<Event.Type, Event> builder = builderWithout(event.type());
        builder.put(event.type(), event);
        List<Event> logCopy = new ArrayList<>(log);
        logCopy.add(event);
        return new History(builder.build(), logCopy, maxLogSize);
    }

    /** Returns a copy of this history with the given event type removed (or an identical history if it was not
     * present) and the log unchanged. */
    public History without(Event.Type type) {
        return new History(builderWithout(type).build(), log, maxLogSize);
    }

    private ImmutableMap.Builder<Event.Type, Event> builderWithout(Event.Type type) {
        ImmutableMap.Builder<Event.Type, Event> builder = new ImmutableMap.Builder<>();
        for (Event event : events.values())
            if (event.type() != type)
                builder.put(event.type(), event);
        return builder;
    }

    /** Returns a copy of this history with a record of this state transition added, if applicable */
    public History recordStateTransition(Node.State from, Node.State to, Agent agent, Instant at) {
        // If the event is a re-reservation, allow the new one to override the older one.
        if (from == to && from != Node.State.reserved) return this;
        return switch (to) {
            case provisioned -> this.with(new Event(Event.Type.provisioned, agent, at));
            case deprovisioned -> this.with(new Event(Event.Type.deprovisioned, agent, at));
            case ready -> this.withoutApplicationEvents().with(new Event(Event.Type.readied, agent, at));
            case active -> this.with(new Event(Event.Type.activated, agent, at));
            case inactive -> this.with(new Event(Event.Type.deactivated, agent, at));
            case reserved -> this.with(new Event(Event.Type.reserved, agent, at));
            case failed -> this.with(new Event(Event.Type.failed, agent, at));
            case dirty -> this.with(new Event(Event.Type.deallocated, agent, at));
            case parked -> this.with(new Event(Event.Type.parked, agent, at));
            case breakfixed -> this.with(new Event(Event.Type.breakfixed, agent, at));
        };
    }
    
    /** 
     * Events can be application or node level. 
     * This returns a copy of this history with all application level events removed and the log unchanged.
     */
    private History withoutApplicationEvents() {
        return new History(events().stream().filter(e -> ! e.type().isApplicationLevel()).collect(Collectors.toList()), log);
    }

    /** Returns the empty history */
    public static History empty() { return new History(List.of(), List.of()); }

    @Override
    public String toString() {
        if (events.isEmpty()) return "history: (empty)";
        StringBuilder b = new StringBuilder("history: ");
        for (Event e : events.values())
            b.append(e).append(", ");
         b.setLength(b.length() - 2); // remove last comma
        return b.toString();
    }

    /** An event which may happen to a node */
    public record Event(Type type, Agent agent, Instant at) {

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

            // The node was scheduled for retirement (hard)
            wantToRetire(false),
            // The node was scheduled for retirement (soft)
            preferToRetire(false),
            // This node was scheduled for failing
            wantToFail,
            // The active node was retired
            retired,
            // The active node went down according to the service monitor
            down,
            // The active node came up according to the service monitor
            up,
            // The node resources/flavor were changed
            resized(false),
            // The node was rebooted
            rebooted(false),
            // The node upgraded its OS (implies a reboot)
            osUpgraded(false),
            // The node verified its firmware (whether this resulted in a reboot depends on the node model)
            firmwareVerified(false);

            private final boolean applicationLevel;

            /** Creates an application level event */
            Type() {
                this.applicationLevel = true;
            }

            Type(boolean applicationLevel) {
                this.applicationLevel = applicationLevel;
            }

            /** Returns true if this is an application-level event and false if it's a node-level event */
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

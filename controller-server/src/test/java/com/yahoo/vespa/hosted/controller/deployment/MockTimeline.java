// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.test.ManualClock;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Simulates concurrent series of events where time is a significant factor.
 *
 * Each event is modelled as a runnable and a time at which to run.
 * A priority queue keeps all pending events, sorted on when they will run.
 * A manual clock is used to keep the time, and is advanced to the point in
 * time where an event will happen, right before running the event.
 * For events with exactly the same time of happening, the order is undefined.
 *
 * An event may be added with a fixed time at which to run, or with a delay
 * relative to the current time in the timeline. The latter can be used to
 * chain several events together, by letting each event add its successor.
 * The time may similarly be advanced to a given instant, or by a given duration.
 *
 * @author jvenstad
 */
public class MockTimeline {

    private final ManualClock clock;
    private final PriorityQueue<Event> events;

    public MockTimeline(ManualClock clock) {
        this.events = new PriorityQueue<>(Comparator.comparing(Event::at));
        this.clock = clock;
    }

    /** Makes the given event happen at the given instant. */
    public void at(Instant instant, Runnable event) {
        if (instant.isBefore(now()))
            throw new IllegalArgumentException("The flow of time runs only one way, my friend.");
        events.add(new Event(instant, event));
    }

    /** Makes the given event happen after the given delay. */
    public void in(Duration delay, Runnable event) {
        at(now().plus(delay), event);
    }

    /** Makes the given event happen with the given period, starting with the given delay from now. */
    public void every(Duration period, Duration delay, Runnable event) {
        in(delay, () -> {
            every(period, event);
            event.run();
        });
    }

    /** Makes the given event happen with the given period, starting with one period delay from now. */
    public void every(Duration period, Runnable event) {
        every(period, period, event);
    }

    /** Returns the current time, as measured by the internal clock. */
    public Instant now() {
        return clock.instant();
    }

    /** Returns whether pending events remain in the timeline. */
    public boolean hasNext() {
        return ! events.isEmpty();
    }

    /** Advances time to the next event, let it happen, and return the time of this event. */
    public Instant next() {
        Event event = events.poll();
        clock.advance(Duration.ofMillis(now().until(event.at(), ChronoUnit.MILLIS)));
        event.happen();
        return event.at();
    }

    /** Advances the time until the given instant, letting all events from now to then happen. */
    public void advance(Instant until) {
        at(until, () -> {});
        while (next() != until);
    }

    /** Advances the time by the given duration, letting all events from now to then happen. */
    public void advance(Duration duration) {
        advance(now().plus(duration));
    }


    private static class Event {

        private final Instant at;
        private final Runnable event;

        private Event(Instant at, Runnable event) {
            this.at = at;
            this.event = event;
        }

        public Instant at() { return at; }
        public void happen() { event.run(); }

    }

}

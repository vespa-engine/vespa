// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.test.ManualClock;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.PriorityQueue;

/**
 * @author jvenstad
 */
public class MockTimeline {

    private final ManualClock clock;
    private final PriorityQueue<Event> events;

    public MockTimeline(ManualClock clock) {
        this.events = new PriorityQueue<>();
        this.clock = clock;
    }

    /** Make @event happen at time @at, as measured by the internal clock. */
    public void at(Instant at, Runnable event) {
        if (at.isBefore(now()))
            throw new IllegalArgumentException("The flow of time runs only one way, my friend.");
        events.add(new Event(at, event));
    }

    /** Make @event happen in @in time, as measured by the internal clock. */
    public void in(Duration in, Runnable event) {
        at(now().plus(in), event);
    }

    /** Make @event happen every @period time, starting @offset time from @now(), as measured by the internal clock. */
    public void every(Duration period, Duration offset, Runnable event) {
        in(offset, () -> {
            every(period, event);
            event.run();
        });
    }

    /** Make @event happen every @period time, starting @period time from @now(), as measured by the internal clock. */
    public void every(Duration period, Runnable event) {
        every(period, period, event);
    }

    /** Returns the current time, as measured by the internal clock. */
    public Instant now() {
        return clock.instant();
    }

    /** Returns whether there are more events in the timeline, or not. */
    public boolean hasNext() {
        return ! events.isEmpty();
    }

    /** Advance time to the next event, let it happen, and return the time of this event. */
    public Instant next() {
        Event event = events.poll();
        clock.advance(Duration.ofMillis(now().until(event.at(), ChronoUnit.MILLIS)));
        event.happen();
        return event.at();
    }

    /** Advance the time until @until, letting all events from now to then happen. */
    public void advance(Instant until) {
        at(until, () -> {});
        while (next() != until);
    }

    /** Advance the time by @duration, letting all events from now to then happen. */
    public void advance(Duration duration) {
        advance(now().plus(duration));
    }

    /** Let the timeline unfold! Careful about those @every-s, though... */
    public void unfold() {
        while (hasNext())
            next();
    }


    private static class Event implements Comparable<Event> {

        private final Instant at;
        private final Runnable event;

        private Event(Instant at, Runnable event) {
            this.at = at;
            this.event = event;
        }

        public Instant at() { return at; }
        public void happen() { event.run(); }


        @Override
        public int compareTo(Event other) {
            return at().compareTo(other.at());
        }

    }

}

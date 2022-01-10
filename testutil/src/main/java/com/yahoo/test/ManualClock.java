// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import com.yahoo.component.annotation.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAmount;
import java.util.concurrent.atomic.AtomicReference;

/** 
 * A clock which initially has the time of its creation but can only be advanced by calling advance
 * 
 * @author bratseth
 */
public class ManualClock extends Clock {

    private final AtomicReference<Instant> currentTime = new AtomicReference<>(Instant.now());

    @Inject
    public ManualClock() {}

    public ManualClock(String utcIsoTime) {
        this(at(utcIsoTime));
    }

    public ManualClock(Instant currentTime) {
        setInstant(currentTime);
    }

    public void advance(TemporalAmount temporal) {
        currentTime.updateAndGet(time -> time.plus(temporal));
    }

    /** Move time backwards by the given amount */
    public void retreat(TemporalAmount temporal) {
        currentTime.updateAndGet(time -> time.minus(temporal));
    }

    public void setInstant(Instant time) {
        currentTime.set(time);
    }

    @Override
    public Instant instant() { return currentTime.get(); }

    @Override
    public ZoneId getZone() { return ZoneOffset.UTC; }

    @Override
    public Clock withZone(ZoneId zone) { return this; }

    @Override
    public long millis() { return instant().toEpochMilli(); }

    public static Instant at(String utcIsoTime) {
        return LocalDateTime.parse(utcIsoTime, DateTimeFormatter.ISO_DATE_TIME).atZone(ZoneOffset.UTC).toInstant();
    }

    @Override
    public String toString() {
        return "ManualClock{" +
                "currentTime=" + currentTime +
                '}';
    }
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAmount;

/**
 * A clock which initially has the time of its creation but can only be advanced by calling advance
 *
 * @author bratseth
 */
public class ManualClock extends Clock {

    private Instant currentTime = Instant.now();

    public ManualClock() {}

    public ManualClock(String utcIsoTime) {
        this(at(utcIsoTime));
    }

    public ManualClock(Instant currentTime) {
        this.currentTime = currentTime;
    }

    public void advance(TemporalAmount temporal) {
        currentTime = currentTime.plus(temporal);
    }

    public void setInstant(Instant time) {
        currentTime = time;
    }

    @Override
    public Instant instant() { return currentTime; }

    @Override
    public ZoneId getZone() { return null; }

    @Override
    public Clock withZone(ZoneId zone) { return null; }

    @Override
    public long millis() { return currentTime.toEpochMilli(); }

    public static Instant at(String utcIsoTime) {
        return LocalDateTime.parse(utcIsoTime, DateTimeFormatter.ISO_DATE_TIME).atZone(ZoneOffset.UTC).toInstant();
    }

}

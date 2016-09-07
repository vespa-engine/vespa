// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles a timeout logic by providing higher level abstraction for asking if there is time left.
 *
 * @author lulf
 * @since 5.1
 */
public class TimeoutBudget {
    private final Clock clock;
    private final Instant startTime;
    private final List<Instant> measurements = new ArrayList<>();
    private final Instant endTime;

    public TimeoutBudget(Clock clock, Duration duration) {
        this.clock = clock;
        this.startTime = clock.instant();
        this.endTime = startTime.plus(duration);
    }

    public Duration timeLeft() {
        Instant now = clock.instant();
        measurements.add(now);
        Duration duration = Duration.between(now, endTime);
        return duration.isNegative() ? Duration.ofMillis(0) : duration;
    }

    public boolean hasTimeLeft() {
        Instant now = clock.instant();
        measurements.add(now);
        return now.isBefore(endTime);
    }

    public String timesUsed() {
        StringBuilder buf = new StringBuilder();
        buf.append("[");
        Instant prev = startTime;
        for (Instant m : measurements) {
            buf.append(Duration.between(prev, m).toMillis());
            prev = m;
            buf.append(" ms, ");
        }
        Instant now = clock.instant();
        buf.append("total: ");
        buf.append(Duration.between(startTime, now).toMillis());
        buf.append(" ms]");
        return buf.toString();
    }

}

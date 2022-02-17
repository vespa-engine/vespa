// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.concurrent.UncheckedTimeoutException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Handles a timeout logic by providing higher level abstraction for asking if there is time left.
 *
 * @author Ulf Lilleengen
 */
public class TimeoutBudget {

    private final Clock clock;
    private final Instant startTime;
    private final List<Measurement> measurements = new ArrayList<>();
    private final Instant endTime;

    public TimeoutBudget(Clock clock, Duration duration) {
        this.clock = clock;
        this.startTime = clock.instant();
        this.endTime = startTime.plus(duration);
    }

    public Duration timeout() {
        return Duration.between(startTime, endTime);
    }

    public Duration timeLeft() {
        Instant now = clock.instant();
        Duration duration = Duration.between(now, endTime);
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    public boolean hasTimeLeft() {
        return clock.instant().isBefore(endTime);
    }

    public boolean hasTimeLeft(String step) {
        Instant now = clock.instant();
        measurements.add(new Measurement(now, step));
        return now.isBefore(endTime);
    }

    public String timesUsed() {
        StringBuilder buf = new StringBuilder();
        buf.append("[");
        Instant prev = startTime;
        for (Measurement m : measurements) {
            if ( ! m.label().isEmpty()) {
                buf.append(m.label()).append(": ");
            }
            buf.append(Duration.between(prev, m.timestamp()).toMillis())
                    .append(" ms")
                    .append(", ");
            prev = m.timestamp();
        }
        Instant now = clock.instant();
        buf.append("total: ");
        buf.append(Duration.between(startTime, now).toMillis());
        buf.append(" ms]");
        return buf.toString();
    }

    /**
     * @param exceptionMessage exception message for the exception that will be thrown if there is no time left
     * @throws UncheckedTimeoutException if this has no time left
     */
    public void assertNotTimedOut(Supplier<String> exceptionMessage) {
        if (hasTimeLeft()) return;
        throw new UncheckedTimeoutException(exceptionMessage.get());
    }

    private static class Measurement {

        private final Instant timestamp;
        private final String label;

        Measurement(Instant timestamp, String label) {
            this.timestamp = timestamp;
            this.label = label;
        }

        public Instant timestamp() {
            return timestamp;
        }

        public String label() {
            return label;
        }
    }
}

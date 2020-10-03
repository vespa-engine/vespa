// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.time.Duration;
import java.util.Optional;

/**
 * Represents a sum and count of Duration.
 *
 * @author hakon
 */
public class DurationSum {

    private final Duration duration;
    private final int count;

    DurationSum(Duration duration, int count) {
        this.duration = duration;
        this.count = count;
    }

    public Duration duration() { return duration; }
    public int count() { return count; }

    public Optional<Duration> averageDuration() {
        if (count <= 0) {
            return Optional.empty();
        }

        long averageMillis = Math.round(duration.toMillis() / (double) count);
        return Optional.of(Duration.ofMillis(averageMillis));
    }

    @Override
    public String toString() {
        return "DurationSum{" +
                "duration=" + duration +
                ", count=" + count +
                '}';
    }
}

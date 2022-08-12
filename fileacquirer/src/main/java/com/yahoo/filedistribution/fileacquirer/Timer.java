// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.filedistribution.fileacquirer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Handles timeout of a task.
 * @author Tony Vaagenes
 */
class Timer {
    private final long endTime;

    private Duration timeLeft() {
        return Duration.ofNanos(endTime - System.nanoTime());
    }

    public Timer(long timeout, TimeUnit timeUnit) {
        endTime = System.nanoTime() + timeUnit.toNanos(timeout);
    }

    public long timeLeft(TimeUnit timeUnit) {
        long remaining = timeUnit.convert(timeLeft().toMillis(), TimeUnit.MILLISECONDS);

        if (remaining > 0)
            return remaining;
        else
            throw new TimeoutException("Timed out");
    }

    public boolean isTimeLeft() {
        return ! timeLeft().isNegative();
    }
}

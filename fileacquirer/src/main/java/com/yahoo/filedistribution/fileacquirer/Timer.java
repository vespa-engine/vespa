// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.filedistribution.fileacquirer;

import java.util.concurrent.TimeUnit;

/**
 * Handles timeout of a task.
 * @author Tony Vaagenes
 */
class Timer {
    private final long endTime;

    private long timeLeft() {
        return endTime - System.currentTimeMillis();
    }

    public Timer(long timeout, TimeUnit timeUnit) {
        endTime = System.currentTimeMillis() + timeUnit.toMillis(timeout);
    }

    public long timeLeft(TimeUnit timeUnit) {
        long remaining = timeUnit.convert(timeLeft(), TimeUnit.MILLISECONDS);

        if (remaining > 0)
            return remaining;
        else
            throw new TimeoutException("Timed out");
    }

    public boolean isTimeLeft() {
        return timeLeft() > 0;
    }
}

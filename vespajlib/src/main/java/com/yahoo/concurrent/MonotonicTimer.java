package com.yahoo.concurrent;

import java.util.concurrent.TimeUnit;

/**
 * Implements the Timer interface by using System.nanoTime.
 *
 * @author baldersheim
 */
public class MonotonicTimer implements Timer {
    @Override
    public long milliTime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }
}

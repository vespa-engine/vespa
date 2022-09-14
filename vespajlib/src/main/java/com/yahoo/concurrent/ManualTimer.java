// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

/**
 * Simple manual Timer for use in tests
 * @author baldersheim
 */
public class ManualTimer implements Timer {

    private long millis = 0;
    public void set(long ms) { millis = ms; }
    public void advance(long ms) { millis += ms; }

    @Override
    public long milliTime() {
        return millis;
    }
}

// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.test;

import java.util.logging.Logger;

/**
 * Unit tests want to fast forward time to avoid waiting for time to pass
 */
public class FakeClock extends SettableClock {
    private static final Logger logger = Logger.getLogger(FakeClock.class.getName());
    protected long currentTime = 1;

    @Override
    public long getTimeInMillis() {
        return currentTime;
    }

    @Override
    public void adjust(long adjustment) {
        synchronized (this) {
            logger.fine("Adjusting clock, adding " + adjustment + " ms to it.");
            currentTime += adjustment;
            notifyAll();
        }
    }

    @Override
    public void set(long newTime) {
        synchronized (this) {
            if (newTime < currentTime) {
                // throw new IllegalArgumentException("Clock attempted to be set to go backwards");
            }
            currentTime = newTime;
        }
    }
}

// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.logging.Level;
import com.yahoo.vespa.clustercontroller.core.testutils.LogFormatter;

import java.util.logging.Logger;

/**
 * FakeTimer
 *
 * Used to fake timing for unit test purposes.
 */
public class FakeTimer implements Timer {

    private static final Logger log = Logger.getLogger(FakeTimer.class.getName());

    static {
        LogFormatter.initializeLogging();
    }

    // Don't start at zero. Clock users may initialize a 'last run' entry with 0, and we want first time to always look like a timeout
    private long currentTime = (long) 30 * 365 * 24 * 60 * 60 * 1000;

    public synchronized long getCurrentTimeInMillis() {
        return currentTime;
    }

    public synchronized void advanceTime(long time) {
        long currentTime = getCurrentTimeInMillis();
        this.currentTime += time;
        log.log(Level.FINE, "Time advanced by " + time + " ms. Time increased from " + currentTime + " to " + (currentTime + time));
        notifyAll();
    }

}

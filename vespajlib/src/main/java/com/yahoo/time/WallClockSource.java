// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.time;

import com.google.common.annotations.Beta;

/**
 * A source for high-resolution timestamps.
 *
 * @author arnej27959
 */

@Beta
public class WallClockSource {

    private volatile long offset;

    /**
     * Obtain the current time in nanoseconds.
     * The epoch is January 1, 1970 UTC just as for System.currentTimeMillis(),
     * but with greater resolution.  Note that the absolute accuracy may be
     * no better than currentTimeMills().
     * @return nanoseconds since the epoch.
     **/
    public final long currentTimeNanos() {
        return System.nanoTime() + offset;
    }

    /**
     * Create a source with 1 millisecond accuracy at start.
     **/
    WallClockSource() {
        long actual = System.currentTimeMillis();
        actual *= 1000000;
        this.offset = actual - System.nanoTime();
        initialAdjust();
    }

    /** adjust the clock source from currentTimeMillis()
     * to ensure that it is no more than 1 milliseconds off.
     * @return true if we want adjust called again soon
     **/
    boolean adjust() {
        long nanosB = System.nanoTime();
        long actual = System.currentTimeMillis();
        long nanosA = System.nanoTime();
        if (nanosA - nanosB > 100000) {
            return true; // not a good time to adjust, try again soon
        }
        return adjustOffset(nanosB, actual, nanosA);
    }

    private boolean adjustOffset(long before, long actual, long after) {
        actual *= 1000000; // convert millis to nanos
        if (actual > after + offset) {
            // System.out.println("WallClockSource adjust UP "+(actual-after-offset));
            offset = actual - after;
            return true;
        }
        if (actual + 999999 < before + offset) {
            // System.out.println("WallClockSource adjust DOWN "+(before+offset-actual-999999));
            offset = actual + 999999 - before;
            return true;
        }
        return false;
    }

    private void initialAdjust() {
       for (int i = 0; i < 100; i++) {
           long nanosB = System.nanoTime();
           long actual = System.currentTimeMillis();
           long nanosA = System.nanoTime();
           adjustOffset(nanosB, actual, nanosA);
       }
    }


    static private WallClockSource autoAdjustingInstance = new WallClockSource();

    /**
     * Get a WallClockSource which auto adjusts to wall clock time.
     **/
    static public WallClockSource get() {
        autoAdjustingInstance.startAdjuster();
        return autoAdjustingInstance;
    }

    private Thread adjuster;

    private synchronized void startAdjuster() {
        if (adjuster == null) {
            adjuster = new AdjustThread();
            adjuster.setDaemon(true);
            adjuster.start();
        }
    }

    private class AdjustThread extends Thread {
        public void run() {
            int millis = 0;
            int nanos = 313373; // random number
            while (true) {
                try {
                    sleep(millis, nanos);
                    if (++millis > 4321) {
                        millis = 1000; // do not sleep too long
                    }
                } catch (InterruptedException e) {
                    return;
                }
                if (adjust()) {
                    // adjust more often in case clock jumped
                    millis = 0;
                }
            }
        }
    }

}

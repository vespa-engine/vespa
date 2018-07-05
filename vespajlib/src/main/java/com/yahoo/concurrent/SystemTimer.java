// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import java.util.concurrent.TimeUnit;

/**
 * This is an implementation of {@link Timer} that is backed by an actual system timer.
 *
 * @author Simon Thoresen Hult
 */
public enum SystemTimer implements Timer {

    INSTANCE;

    private volatile long millis;

    private SystemTimer() {
        millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        Thread thread = new Thread() {

            @Override
            public void run() {
                while (true) {
                    millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public long milliTime() {
        return millis;
    }
}

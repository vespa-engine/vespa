// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.concurrent;

import java.time.Duration;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Execute a runnable exactly once in a background thread.
 *
 * @author mpolden
 */
public class Once extends TimerTask {

    private static final Logger log = Logger.getLogger(Once.class.getName());

    private final Runnable runnable;
    private final Timer timer = new Timer(true);

    // private to avoid exposing run method
    private Once(Runnable runnable, Duration delay) {
        this.runnable = Objects.requireNonNull(runnable, "runnable must be non-null");
        Objects.requireNonNull(delay, "delay must be non-null");
        timer.schedule(this, delay.toMillis());
    }

    /** Execute runnable after given delay */
    public static void after(Duration delay, Runnable runnable) {
        new Once(runnable, delay);
    }

    @Override
    public void run() {
        try {
            runnable.run();
        } catch (Throwable t) {
            log.log(Level.WARNING, "Task '" + runnable + "' failed", t);
        } finally {
            timer.cancel();
        }
    }

}

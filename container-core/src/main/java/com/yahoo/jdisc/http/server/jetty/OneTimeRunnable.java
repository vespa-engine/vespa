// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Tony Vaagenes
 */
public class OneTimeRunnable {
    private final Runnable runnable;
    private final AtomicBoolean hasRun = new AtomicBoolean(false);

    public OneTimeRunnable(Runnable runnable) {
        this.runnable = runnable;
    }

    public void runIfFirstInvocation() {
        boolean previous = hasRun.getAndSet(true);
        if (!previous) {
            runnable.run();
        }
    }
}

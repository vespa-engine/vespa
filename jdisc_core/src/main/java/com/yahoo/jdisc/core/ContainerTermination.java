// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.application.DeactivatedContainer;

/**
 * @author Simon Thoresen Hult
 */
public class ContainerTermination implements DeactivatedContainer, Runnable {

    private final Object lock = new Object();
    private final Object appContext;
    private Runnable task;
    private boolean done;

    public ContainerTermination(Object appContext) {
        this.appContext = appContext;
    }

    @Override
    public Object appContext() {
        return appContext;
    }

    @Override
    public void notifyTermination(Runnable task) {
        boolean done;
        synchronized (lock) {
            if (this.task != null) {
                throw new IllegalStateException();
            }
            this.task = task;
            done = this.done;
        }
        if (done) {
            task.run();
        }
    }

    @Override
    public void run() {
        Runnable task;
        synchronized (lock) {
            done = true;
            task = this.task;
        }
        if (task != null) {
            task.run();
        }
    }
}

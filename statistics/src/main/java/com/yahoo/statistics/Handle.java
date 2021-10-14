// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


import java.util.TimerTask;


/**
 * Base class for the interface to the statistics framework.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public abstract class Handle {

    private TimerTask task;

    private final String name;
    private boolean cancelled;
    private final Statistics manager;
    private final Callback parametrizedCallback;
    private boolean firstTime;

    Handle(String name, Statistics manager, Callback parametrizedCallback) {
        this.name = name;
        this.manager = manager;
        this.parametrizedCallback = parametrizedCallback;
        firstTime = true;
    }

    String getName() {
        return name;
    }

    TimerTask makeTask() {
        final Handle self = this;
        synchronized (self) {
            if (task != null) {
                task.cancel();
            }
            task = new TimerTask() {
                public void run() {
                    self.run();
                }
            };
            return task;
        }
    }

    /**
     * Run the callback object.
     *
     * This will happen at the start of each invocation of a Handle's
     * run() method. The callback is presumed to be exception safe.
     * If no callback is set, this is a no-op. The callback will need
     * to handle any necessary synchronization itself.
     */
    public final void runCallback() {
        if (parametrizedCallback == null) {
            return;
        }
        parametrizedCallback.run(this, firstTime);
        firstTime = false;
    }

    /**
     * Run the callback object first, then invoke runHandle().
     */
    public final void run() {
        runCallback();
        runHandle();
    }

    /**
     * Invoke an action to be performed periodically for a statistics Handle.
     *
     * <p>Synchronization has to be handled by the method itself.
     */
    public abstract void runHandle();

    /**
     * Cancel this Handle and remove it from internal state in Statistics.
     *
     * @return value of java.util.TimerTask.cancel()
     */
    public final boolean cancel() {
        boolean ok = (task == null ? false : task.cancel());
        cancelled = true;
        manager.purge();
        return ok;
    }

    /**
     * Returns whether this object has been cancelled or not.
     *
     * @return true if cancelled
     */
    public final boolean isCancelled() {
        return cancelled;
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import java.io.Closeable;
import java.time.Duration;
import java.util.function.BiConsumer;

/**
 * CloseableInvoker is an abstract implementation of {@link Closeable} with an additional hook for
 * executing code at closing. Classes that extend CloseableInvoker need to override {@link #release()}
 * instead of {@link #close()} which is final to avoid accidental overriding.
 *
 * @author ollivir
 */
public abstract class CloseableInvoker implements Closeable {

    protected abstract void release();

    private BiConsumer<Boolean, RequestDuration> teardown = null;
    private boolean success = false;
    private RequestDuration duration;

    public void teardown(BiConsumer<Boolean, RequestDuration> teardown) {
        this.teardown = teardown;
        this.duration = new RequestDuration();
    }

    protected void setFinalStatus(boolean success) {
        this.success = success;
    }

    @Override
    public final void close() {
        if (teardown != null) {
            teardown.accept(success, duration.complete());
            teardown = null;
        }
        release();
    }

}

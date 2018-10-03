// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import java.io.Closeable;

/**
 * CloseableInvoker is an abstract implementation of {@link Closeable} with an additional hook for
 * executing code at closing. Classes that extend CloseableInvoker need to override {@link #release()}
 * instead of {@link #close()} which is final to avoid accidental overriding.
 *
 * @author ollivir
 */
public abstract class CloseableInvoker implements Closeable {
    protected abstract void release();

    private Runnable teardown = null;

    public void teardown(Runnable teardown) {
        this.teardown = teardown;
    }

    @Override
    public final void close() {
        if (teardown != null) {
            teardown.run();
            teardown = null;
        }
        release();
    }
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.google.common.util.concurrent.AbstractFuture;

/**
 * <p>This class provides an implementation of {@link CompletionHandler} that allows you to wait for either {@link
 * #completed()} or {@link #failed(Throwable)} to be called. If failed() was called, the corresponding Throwable will
 * be rethrown when calling either of the get() methods. Unless an exception is thrown, the get() methods will always
 * return Boolean.TRUE.</p>
 *
 * <p>Notice that calling {@link #cancel(boolean)} throws an UnsupportedOperationException.</p>
 *
 * @author Simon Thoresen Hult
 */
public final class FutureCompletion extends AbstractFuture<Boolean> implements CompletionHandler {

    @Override
    public void completed() {
        set(true);
    }

    @Override
    public void failed(Throwable t) {
        setException(t);
    }

    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean isCancelled() {
        return false;
    }
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.google.common.util.concurrent.MoreExecutors;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

/**
 * @author Simon Thoresen Hult
 */
public class FutureCompletionTestCase {

    @Test
    public void requireThatCancelIsUnsupported() {
        FutureCompletion future = new FutureCompletion();
        assertFalse(future.isCancelled());
        try {
            future.cancel(true);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        assertFalse(future.isCancelled());
        try {
            future.cancel(false);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        assertFalse(future.isCancelled());
    }

    @Test
    public void requireThatCompletedReturnsTrue() throws Exception {
        FutureCompletion future = new FutureCompletion();
        try {
            future.get(0, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {

        }
        future.completed();
        assertTrue(future.get(0, TimeUnit.MILLISECONDS));
        assertTrue(future.get());
    }

    @Test
    public void requireThatCompletionIsDoneWhenCompleted() {
        FutureCompletion future = new FutureCompletion();
        assertFalse(future.isDone());
        future.completed();
        assertTrue(future.isDone());
    }

    @Test
    public void requireThatCompletionIsDoneWhenFailed() {
        FutureCompletion future = new FutureCompletion();
        assertFalse(future.isDone());
        future.failed(new Throwable());
        assertTrue(future.isDone());
    }

    @Test
    public void requireThatFailedCauseIsRethrown() throws Exception {
        FutureCompletion future = new FutureCompletion();
        Throwable t = new Throwable();
        future.failed(t);
        try {
            future.get(0, TimeUnit.SECONDS);
            fail();
        } catch (ExecutionException e) {
            assertSame(t, e.getCause());
        }
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertSame(t, e.getCause());
        }
    }

    @Test
    public void requireThatCompletionCanBeListenedTo() throws InterruptedException {
        FutureCompletion completion = new FutureCompletion();
        RunnableLatch listener = new RunnableLatch();
        completion.addListener(listener, MoreExecutors.directExecutor());
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        completion.completed();
        assertTrue(listener.await(600, TimeUnit.SECONDS));

        completion = new FutureCompletion();
        listener = new RunnableLatch();
        completion.addListener(listener, MoreExecutors.directExecutor());
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        completion.failed(new Throwable());
        assertTrue(listener.await(600, TimeUnit.SECONDS));
    }
}

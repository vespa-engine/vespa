// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author freva
 */
public class NodeAgentContextManagerTest {

    private static final int TIMEOUT = 10_000;

    private final Clock clock = Clock.systemUTC();
    private final NodeAgentContext initialContext = generateContext();
    private final NodeAgentContextManager manager = new NodeAgentContextManager(clock, initialContext);

    @Test(timeout = TIMEOUT)
    public void returns_immediately_if_next_context_is_ready() throws InterruptedException {
        NodeAgentContext context1 = generateContext();
        manager.scheduleTickWith(context1, clock.instant());

        assertSame(initialContext, manager.currentContext());
        assertSame(context1, manager.nextContext());
        assertSame(context1, manager.currentContext());
    }

    @Test(timeout = TIMEOUT)
    public void returns_no_earlier_than_at_given_time() throws InterruptedException {
        NodeAgentContext context1 = generateContext();
        Instant returnAt = clock.instant().plusMillis(500);
        manager.scheduleTickWith(context1, returnAt);

        assertSame(initialContext, manager.currentContext());
        assertSame(context1, manager.nextContext());
        assertSame(context1, manager.currentContext());
        // Is accurate to a millisecond
        assertFalse(clock.instant().plusMillis(1).isBefore(returnAt));
    }

    @Test(timeout = TIMEOUT)
    public void blocks_in_nextContext_until_one_is_scheduled() throws InterruptedException {
        AsyncExecutor<NodeAgentContext> async = new AsyncExecutor<>(manager::nextContext);
        assertFalse(async.response.isPresent());
        Thread.sleep(10);
        assertFalse(async.response.isPresent());

        NodeAgentContext context1 = generateContext();
        manager.scheduleTickWith(context1, clock.instant());

        async.awaitResult();
        assertEquals(Optional.of(context1), async.response);
        assertFalse(async.exception.isPresent());
    }

    @Test(timeout = TIMEOUT)
    public void blocks_in_nextContext_until_interrupt() throws InterruptedException {
        AsyncExecutor<NodeAgentContext> async = new AsyncExecutor<>(manager::nextContext);
        assertFalse(async.response.isPresent());
        Thread.sleep(10);
        assertFalse(async.response.isPresent());

        manager.interrupt();

        async.awaitResult();
        assertEquals(Optional.of(InterruptedException.class), async.exception.map(Exception::getClass));
        assertFalse(async.response.isPresent());
    }

    @Test(timeout = TIMEOUT)
    public void setFrozen_does_not_block_with_no_timeout() throws InterruptedException {
        assertFalse(manager.setFrozen(false, Duration.ZERO));

        // Generate new context and get it from the supplier, this completes the unfreeze
        NodeAgentContext context1 = generateContext();
        manager.scheduleTickWith(context1, clock.instant());
        assertSame(context1, manager.nextContext());

        assertTrue(manager.setFrozen(false, Duration.ZERO));
    }

    @Test(timeout = TIMEOUT)
    public void setFrozen_blocks_at_least_for_duration_of_timeout() {
        long wantedDurationMillis = 100;
        long start = clock.millis();
        assertFalse(manager.setFrozen(false, Duration.ofMillis(wantedDurationMillis)));
        long actualDurationMillis = clock.millis() - start;

        assertTrue(actualDurationMillis >= wantedDurationMillis);
    }

    @Test(timeout = TIMEOUT)
    public void setFrozen_is_successful_if_converged_in_time() throws InterruptedException {
        AsyncExecutor<Boolean> async = new AsyncExecutor<>(() -> manager.setFrozen(false, Duration.ofMillis(500)));

        assertFalse(async.response.isPresent());

        NodeAgentContext context1 = generateContext();
        manager.scheduleTickWith(context1, clock.instant());
        assertSame(context1, manager.nextContext());

        async.awaitResult();
        assertEquals(Optional.of(true), async.response);
        assertFalse(async.exception.isPresent());
    }

    private static NodeAgentContext generateContext() {
        return new NodeAgentContextImpl.Builder("container-123.domain.tld").build();
    }

    private static class AsyncExecutor<T> {
        private final Object monitor = new Object();
        private final Thread thread;
        private volatile Optional<T> response = Optional.empty();
        private volatile Optional<Exception> exception = Optional.empty();
        private boolean completed = false;

        private AsyncExecutor(ThrowingSupplier<T> supplier) {
            this.thread = new Thread(() -> {
                try {
                    response = Optional.of(supplier.get());
                } catch (Exception e) {
                    exception = Optional.of(e);
                }
                synchronized (monitor) {
                    completed = true;
                    monitor.notifyAll();
                }
            });
            this.thread.start();
        }

        private void awaitResult() {
            synchronized (monitor) {
                while (!completed) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException ignored) { }
                }
            }
        }
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
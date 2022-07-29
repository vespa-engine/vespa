// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextSupplier.ContextSupplierInterruptedException;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author freva
 */
public class NodeAgentContextManagerTest {

    private static final int TIMEOUT = 10_000;

    private final Clock clock = Clock.systemUTC();
    private final NodeAgentContext initialContext = generateContext();
    private final NodeAgentContextManager manager = new NodeAgentContextManager(clock, initialContext);

    @Test
    @Timeout(TIMEOUT)
    void context_is_ignored_unless_scheduled_while_waiting() {
        NodeAgentContext context1 = generateContext();
        manager.scheduleTickWith(context1, clock.instant());
        assertSame(initialContext, manager.currentContext());

        AsyncExecutor<NodeAgentContext> async = new AsyncExecutor<>(manager::nextContext);
        manager.waitUntilWaitingForNextContext();
        assertFalse(async.isCompleted());

        NodeAgentContext context2 = generateContext();
        manager.scheduleTickWith(context2, clock.instant());

        assertSame(context2, async.awaitResult().response.get());
        assertSame(context2, manager.currentContext());
    }

    @Test
    @Timeout(TIMEOUT)
    void returns_no_earlier_than_at_given_time() {
        AsyncExecutor<NodeAgentContext> async = new AsyncExecutor<>(manager::nextContext);
        manager.waitUntilWaitingForNextContext();

        NodeAgentContext context1 = generateContext();
        Instant returnAt = clock.instant().plusMillis(500);
        manager.scheduleTickWith(context1, returnAt);

        assertSame(context1, async.awaitResult().response.get());
        assertSame(context1, manager.currentContext());
        // Is accurate to a millisecond
        assertFalse(clock.instant().plusMillis(1).isBefore(returnAt));
    }

    @Test
    @Timeout(TIMEOUT)
    void blocks_in_nextContext_until_one_is_scheduled() {
        AsyncExecutor<NodeAgentContext> async = new AsyncExecutor<>(manager::nextContext);
        manager.waitUntilWaitingForNextContext();
        assertFalse(async.isCompleted());

        NodeAgentContext context1 = generateContext();
        manager.scheduleTickWith(context1, clock.instant());

        async.awaitResult();
        assertEquals(Optional.of(context1), async.response);
        assertFalse(async.exception.isPresent());
    }

    @Test
    @Timeout(TIMEOUT)
    void blocks_in_nextContext_until_interrupt() {
        AsyncExecutor<NodeAgentContext> async = new AsyncExecutor<>(manager::nextContext);
        manager.waitUntilWaitingForNextContext();
        assertFalse(async.isCompleted());

        manager.interrupt();

        async.awaitResult();
        assertEquals(Optional.of(ContextSupplierInterruptedException.class), async.exception.map(Exception::getClass));
        assertFalse(async.response.isPresent());
    }

    @Test
    @Timeout(TIMEOUT)
    void setFrozen_does_not_block_with_no_timeout() {
        assertFalse(manager.setFrozen(false, Duration.ZERO));

        // Generate new context and get it from the supplier, this completes the unfreeze
        NodeAgentContext context1 = generateContext();
        AsyncExecutor<NodeAgentContext> async = new AsyncExecutor<>(manager::nextContext);
        manager.waitUntilWaitingForNextContext();
        manager.scheduleTickWith(context1, clock.instant());
        assertSame(context1, async.awaitResult().response.get());

        assertTrue(manager.setFrozen(false, Duration.ZERO));
    }

    @Test
    @Timeout(TIMEOUT)
    void setFrozen_blocks_at_least_for_duration_of_timeout() {
        long wantedDurationMillis = 100;
        long start = clock.millis();
        assertFalse(manager.setFrozen(false, Duration.ofMillis(wantedDurationMillis)));
        long actualDurationMillis = clock.millis() - start;

        assertTrue(actualDurationMillis >= wantedDurationMillis);
    }

    private static NodeAgentContext generateContext() {
        return NodeAgentContextImpl.builder("container-123.domain.tld").fileSystem(TestFileSystem.create()).build();
    }

    private static class AsyncExecutor<T> {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Optional<T> response = Optional.empty();
        private volatile Optional<Exception> exception = Optional.empty();

        private AsyncExecutor(Callable<T> supplier) {
            new Thread(() -> {
                try {
                    response = Optional.of(supplier.call());
                } catch (Exception e) {
                    exception = Optional.of(e);
                }
                latch.countDown();
            }).start();
        }

        private AsyncExecutor<T> awaitResult() {
            try {
                latch.await();
            } catch (InterruptedException ignored) { }
            return this;
        }

        private boolean isCompleted() {
            return latch.getCount() == 0;
        }
    }
}
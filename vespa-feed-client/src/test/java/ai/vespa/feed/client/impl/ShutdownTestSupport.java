// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bounded waits for fixtures invoked through non-throwing transport callbacks. */
final class ShutdownTestSupport {
    private ShutdownTestSupport() { }

    static ThreadFactory recordingThreads(AtomicReference<Thread> worker) {
        return task -> {
            Thread thread = new Thread(task, "test-shutdown");
            worker.set(thread);
            return thread;
        };
    }

    static void awaitLifecycleWait(Thread worker, String method) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (worker.isAlive() && System.nanoTime() < deadline) {
            Thread.State state = worker.getState();
            var stack = Arrays.asList(worker.getStackTrace());
            if ((state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) &&
                stack.stream().anyMatch(frame -> frame.getClassName().equals(ShutdownLifecycle.class.getName()))) {
                assertTrue(stack.stream().anyMatch(frame -> frame.getClassName().equals(ShutdownLifecycle.class.getName()) &&
                                                           frame.getMethodName().equals(method)),
                           "cleanup worker reached the wrong wait: " + stack);
                return;
            }
            Thread.yield();
        }
        throw new AssertionError("cleanup worker did not wait in " + method);
    }

    static void awaitLatch(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "test latch was not released");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}

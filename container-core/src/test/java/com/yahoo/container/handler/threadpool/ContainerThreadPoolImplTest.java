// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.threadpool;

import com.yahoo.collections.Tuple2;
import com.yahoo.concurrent.Receiver;
import com.yahoo.container.protect.ProcessTerminator;
import com.yahoo.container.test.MetricMock;
import com.yahoo.jdisc.Metric;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Steinar Knutsen
 * @author bjorncs
 */
public class ContainerThreadPoolImplTest {

    private static final int CPUS = 16;

    @Test
    final void testThreadPool() throws InterruptedException {
        Metric metrics = new MetricMock();
        ContainerThreadpoolConfig config = new ContainerThreadpoolConfig(new ContainerThreadpoolConfig.Builder().maxThreads(1));
        ContainerThreadPool threadPool = new ContainerThreadpoolImpl(config, metrics);
        Executor exec = threadPool.executor();
        Tuple2<Receiver.MessageState, Boolean> reply;
        FlipIt command = new FlipIt();
        for (boolean done = false; !done; ) {
            try {
                exec.execute(command);
                done = true;
            } catch (RejectedExecutionException e) {
                // just try again
            }
        }
        reply = command.didItRun.get(5 * 60 * 1000);
        if (reply.first != Receiver.MessageState.VALID) {
            fail("Executor task probably timed out, five minutes should be enough to flip a boolean.");
        }
        if (reply.second != Boolean.TRUE) {
            fail("Executor task seemed to run, but did not get correct value.");
        }
        threadPool.close();
        command = new FlipIt();
        try {
            exec.execute(command);
        } catch (final RejectedExecutionException e) {
            // this is what should happen
            return;
        }
        fail("Pool did not reject tasks after shutdown.");
    }

    private ThreadPoolExecutor createPool(int maxThreads, int queueSize) {
        return createPool(new MetricMock(), maxThreads, queueSize);
    }
    private ThreadPoolExecutor createPool(Metric metric, int maxThreads, int queueSize) {
        ContainerThreadpoolConfig config = new ContainerThreadpoolConfig(new ContainerThreadpoolConfig.Builder()
                .maxThreads(maxThreads)
                .minThreads(maxThreads)
                .queueSize(queueSize));
        ContainerThreadPool threadPool = new ContainerThreadpoolImpl(
                config, metric, new MockProcessTerminator(), CPUS);
        ExecutorServiceWrapper wrapper = (ExecutorServiceWrapper) threadPool.executor();
        WorkerCompletionTimingThreadPoolExecutor executor = (WorkerCompletionTimingThreadPoolExecutor)wrapper.delegate();
        return executor;
    }

    @Test
    void testThatThreadPoolSizeFollowsConfig() {
        MetricMock metrics = new MetricMock();
        ThreadPoolExecutor executor = createPool(metrics, 3, 1200);
        assertEquals(3, executor.getMaximumPoolSize());
        assertEquals(1200, executor.getQueue().remainingCapacity());
        assertEquals(7, metrics.innvocations().size());
        assertEquals(3L, metrics.innvocations().get("serverThreadPoolSize").val);
        assertEquals(3L, metrics.innvocations().get("jdisc.thread_pool.max_allowed_size").val);
        assertEquals(0L, metrics.innvocations().get("serverActiveThreads").val);
        assertEquals(1200L, metrics.innvocations().get("jdisc.thread_pool.work_queue.capacity").val);
        assertEquals(0L, metrics.innvocations().get("jdisc.thread_pool.work_queue.size").val);
    }

    @Test
    void testThatThreadPoolSizeAutoDetected() {
        MetricMock metrics = new MetricMock();
        ThreadPoolExecutor executor = createPool(metrics, 0, 0);
        assertEquals(CPUS * 4, executor.getMaximumPoolSize());
        assertEquals(0, executor.getQueue().remainingCapacity());
        assertEquals(7, metrics.innvocations().size());
        assertEquals(64L, metrics.innvocations().get("serverThreadPoolSize").val);
        assertEquals(64L, metrics.innvocations().get("jdisc.thread_pool.max_allowed_size").val);
        assertEquals(0L, metrics.innvocations().get("serverActiveThreads").val);
        assertEquals(64L, metrics.innvocations().get("jdisc.thread_pool.work_queue.capacity").val);
        assertEquals(0L, metrics.innvocations().get("jdisc.thread_pool.work_queue.size").val);
    }

    @Test
    void testThatQueueSizeAutoDetected() {
        ThreadPoolExecutor executor = createPool(24, -50);
        assertEquals(24, executor.getMaximumPoolSize());
        assertEquals(24 * 50, executor.getQueue().remainingCapacity());
    }

    @Test
    void testThatThreadPoolSizeAndQueueSizeAutoDetected() {
        ThreadPoolExecutor executor = createPool(0, -100);
        assertEquals(CPUS * 4, executor.getMaximumPoolSize());
        assertEquals(CPUS * 4 * 100, executor.getQueue().remainingCapacity());
    }

    private class FlipIt implements Runnable {
        public final Receiver<Boolean> didItRun = new Receiver<>();

        @Override
        public void run() {
            didItRun.put(Boolean.TRUE);
        }
    }

    // Ignored because it depends on the system time and so is unstable on factory
    @Test
    @Disabled
    void testThreadPoolTerminationOnBreakdown() throws InterruptedException {
        ContainerThreadpoolConfig config = new ContainerThreadpoolConfig(
                new ContainerThreadpoolConfig.Builder()
                        .maxThreads(2)
                        .maxThreadExecutionTimeSeconds(1));
        MockProcessTerminator terminator = new MockProcessTerminator();
        Metric metrics = new MetricMock();
        ContainerThreadPool threadPool = new ContainerThreadpoolImpl(config, metrics, terminator);

        // No dying when threads hang shorter than max thread execution time
        threadPool.executor().execute(new Hang(500));
        threadPool.executor().execute(new Hang(500));
        assertEquals(0, terminator.dieRequests);
        assertRejected(threadPool, new Hang(500)); // no more threads
        assertEquals(0, terminator.dieRequests); // ... but not for long enough yet
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
        }
        threadPool.executor().execute(new Hang(1));
        assertEquals(0, terminator.dieRequests);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
        } // Make sure both threads are available

        // Dying when hanging both thread pool threads for longer than max thread execution time
        threadPool.executor().execute(new Hang(2000));
        threadPool.executor().execute(new Hang(2000));
        assertEquals(0, terminator.dieRequests);
        assertRejected(threadPool, new Hang(2000)); // no more threads
        assertEquals(0, terminator.dieRequests); // ... but not for long enough yet
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
        }
        assertRejected(threadPool, new Hang(2000)); // no more threads
        assertEquals(1, terminator.dieRequests); // ... for longer than maxThreadExecutionTime
    }

    private void assertRejected(ContainerThreadPool threadPool, Runnable task) {
        try {
            threadPool.executor().execute(task);
            fail("Expected execution rejected");
        } catch (final RejectedExecutionException expected) {
        }
    }

    private class Hang implements Runnable {

        private final long hangMillis;

        public Hang(int hangMillis) {
            this.hangMillis = hangMillis;
        }

        @Override
        public void run() {
            try { Thread.sleep(hangMillis); } catch (InterruptedException e) {}
        }

    }

    private static class MockProcessTerminator extends ProcessTerminator {

        public volatile int dieRequests = 0;

        @Override
        public void logAndDie(String message, boolean dumpThreads) {
            dieRequests++;
        }

    }

}
// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import static org.junit.Assert.fail;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import com.yahoo.container.protect.ProcessTerminator;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import com.yahoo.concurrent.Receiver;
import com.yahoo.concurrent.Receiver.MessageState;
import com.yahoo.collections.Tuple2;
import com.yahoo.jdisc.Metric;

import static org.junit.Assert.assertEquals;

/**
 * Check threadpool provider accepts tasks and shuts down properly.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ThreadPoolProviderTestCase {

    @Test
    public final void testThreadPoolProvider() throws InterruptedException {
        ThreadpoolConfig config = new ThreadpoolConfig(new ThreadpoolConfig.Builder().maxthreads(1));
        ThreadPoolProvider provider = new ThreadPoolProvider(config, Mockito.mock(Metric.class));
        Executor exec = provider.get();
        Tuple2<MessageState, Boolean> reply;
        FlipIt command = new FlipIt();
        for (boolean done = false; !done;) {
            try {
                exec.execute(command);
                done = true;
            } catch (RejectedExecutionException e) {
                // just try again
            }
        }
        reply = command.didItRun.get(5 * 60 * 1000);
        if (reply.first != MessageState.VALID) {
            fail("Executor task probably timed out, five minutes should be enough to flip a boolean.");
        }
        if (reply.second != Boolean.TRUE) {
            fail("Executor task seemed to run, but did not get correct value.");
        }
        provider.deconstruct();
        command = new FlipIt();
        try {
            exec.execute(command);
        } catch (final RejectedExecutionException e) {
            // this is what should happen
            return;
        }
        fail("Pool did not reject tasks after shutdown.");
    }

    private class FlipIt implements Runnable {
        public final Receiver<Boolean> didItRun = new Receiver<>();

        @Override
        public void run() {
            didItRun.put(Boolean.TRUE);
        }
    }

    @Test
    @Ignore // Ignored because it depends on the system time and so is unstable on factory
    public void testThreadPoolProviderTerminationOnBreakdown() throws InterruptedException {
        ThreadpoolConfig config = new ThreadpoolConfig(new ThreadpoolConfig.Builder().maxthreads(2)
                                                                                     .maxThreadExecutionTimeSeconds(1));
        MockProcessTerminator terminator = new MockProcessTerminator();
        ThreadPoolProvider provider = new ThreadPoolProvider(config, Mockito.mock(Metric.class), terminator);

        // No dying when threads hang shorter than max thread execution time
        provider.get().execute(new Hang(500));
        provider.get().execute(new Hang(500));
        assertEquals(0, terminator.dieRequests);
        assertRejected(provider, new Hang(500)); // no more threads
        assertEquals(0, terminator.dieRequests); // ... but not for long enough yet
        try { Thread.sleep(1500); } catch (InterruptedException e) {}
        provider.get().execute(new Hang(1));
        assertEquals(0, terminator.dieRequests);
        try { Thread.sleep(50); } catch (InterruptedException e) {} // Make sure both threads are available

        // Dying when hanging both thread pool threads for longer than max thread execution time
        provider.get().execute(new Hang(2000));
        provider.get().execute(new Hang(2000));
        assertEquals(0, terminator.dieRequests);
        assertRejected(provider, new Hang(2000)); // no more threads
        assertEquals(0, terminator.dieRequests); // ... but not for long enough yet
        try { Thread.sleep(1500); } catch (InterruptedException e) {}
        assertRejected(provider, new Hang(2000)); // no more threads
        assertEquals(1, terminator.dieRequests); // ... for longer than maxThreadExecutionTime
    }

    private void assertRejected(ThreadPoolProvider provider, Runnable task) {
        try {
            provider.get().execute(task);
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

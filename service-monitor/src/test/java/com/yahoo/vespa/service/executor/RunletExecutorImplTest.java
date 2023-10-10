// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.executor;

import org.junit.After;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author hakonhall
 */
public class RunletExecutorImplTest {
    private final RunletExecutorImpl executor = new RunletExecutorImpl(2);

    @After
    public void tearDown() {
        executor.close();
    }

    @Test
    public void testAFewCancellations() {
        for (int i = 0; i < 10; ++i) {
            TestRunlet runlet = new TestRunlet();
            Cancellable cancellable = schedule(runlet);
            runlet.waitUntilCompleted(5);
            cancellable.cancel();
            runlet.waitUntilClosed();
        }
    }

    @Test
    public void testCongestedThreadPool() {
        TestRunlet runlet1 = new TestRunlet();
        runlet1.shouldWaitInRun(true);
        Cancellable cancellable1 = schedule(runlet1);
        runlet1.waitUntilInRun();

        TestRunlet runlet2 = new TestRunlet();
        runlet2.shouldWaitInRun(true);
        Cancellable cancellable2 = schedule(runlet2);
        runlet2.waitUntilInRun();

        TestRunlet runlet3 = new TestRunlet();
        Cancellable cancellable3 = schedule(runlet3);
        try { Thread.sleep(10); } catch (InterruptedException ignored) { }
        assertEquals(0, runlet3.getRunsStarted());

        cancellable3.cancel();
        assertTrue(runlet3.isClosed());
        assertEquals(0, runlet3.getRunsStarted());

        runlet1.shouldWaitInRun(false);
        runlet2.shouldWaitInRun(false);
        cancellable1.cancel();
        cancellable2.cancel();
    }

    @Test
    public void testWithoutCancellation() {
        TestRunlet runlet = new TestRunlet();
        Cancellable toBeIgnored = schedule(runlet);
        runlet.waitUntilCompleted(2);
    }

    private Cancellable schedule(Runlet runlet) {
        return executor.scheduleWithFixedDelay(runlet, Duration.ofMillis(20));
    }
}

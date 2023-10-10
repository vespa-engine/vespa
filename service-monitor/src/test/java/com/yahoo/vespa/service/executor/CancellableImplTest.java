// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.executor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hakonhall
 */
public class CancellableImplTest {
    private final TestExecutor executor = new TestExecutor();
    private final TestRunlet runlet = new TestRunlet();
    private final Cancellable cancellable = executor.scheduleWithFixedDelay(runlet, Duration.ofSeconds(1));

    @After
    public void tearDown() {
        executor.close();
    }

    @Before
    public void setUp() {
        assertEquals(0, runlet.getRunsStarted());
        executor.runToCompletion(1);
        assertEquals(1, runlet.getRunsStarted());
        executor.runToCompletion(2);
        assertEquals(2, runlet.getRunsStarted());
        assertTrue(executor.isExecutionRunning());
        assertFalse(runlet.isClosed());
        assertTrue(executor.isExecutionRunning());
        assertFalse(runlet.isClosed());
    }

    @Test
    public void testCancelWhileIdle() {
        // Cancel while runlet is not running and verify closure and executor cancellation
        cancellable.cancel();
        assertFalse(executor.isExecutionRunning());
        assertTrue(runlet.isClosed());

        // Ensure a spurious run is ignored.
        executor.runAsync();
        executor.runToCompletion(3);
        assertEquals(2, runlet.getRunsStarted());
    }

    @Test
    public void testCancelWhileRunning() {
        // halt execution in runlet
        runlet.shouldWaitInRun(true);
        executor.runAsync();
        runlet.waitUntilInRun();
        assertEquals(3, runlet.getRunsStarted());
        assertEquals(2, runlet.getRunsCompleted());
        assertTrue(executor.isExecutionRunning());
        assertFalse(runlet.isClosed());

        // Cancel now
        cancellable.cancel();
        assertTrue(executor.isExecutionRunning());
        assertFalse(runlet.isClosed());

        // Complete the runlet.run(), and verify the close and executor cancellation takes effect
        runlet.shouldWaitInRun(false);
        executor.waitUntilRunCompleted(3);
        assertFalse(executor.isExecutionRunning());
        assertTrue(runlet.isClosed());

        // Ensure a spurious run is ignored.
        executor.runToCompletion(4);
        assertEquals(3, runlet.getRunsStarted());
    }
}

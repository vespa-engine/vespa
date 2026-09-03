// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class DocprocLivenessWatchdogTestCase {

    private static final int SAMPLES_TO_TRIP = 3;

    @Test
    public void testIdleContainerIsNeverStalled() {
        AtomicLong submitted = new AtomicLong(0);
        AtomicLong completed = new AtomicLong(0);
        AtomicBoolean livenessOk = new AtomicBoolean(true);
        DocprocLivenessWatchdog watchdog = watchdog(submitted, completed, livenessOk);

        for (int i = 0; i < 10; i++)
            watchdog.sample();

        assertFalse(watchdog.isStalled());
        assertTrue(livenessOk.get());
    }

    @Test
    public void testProgressingBacklogIsNotStalled() {
        AtomicLong submitted = new AtomicLong(0);
        AtomicLong completed = new AtomicLong(0);
        AtomicBoolean livenessOk = new AtomicBoolean(true);
        DocprocLivenessWatchdog watchdog = watchdog(submitted, completed, livenessOk);

        for (int i = 0; i < 10; i++) {
            submitted.incrementAndGet();
            watchdog.sample();
            completed.incrementAndGet();
        }

        assertFalse(watchdog.isStalled());
        assertTrue(livenessOk.get());
    }

    @Test
    public void testBacklogWithNoProgressUnderThresholdIsNotYetStalled() {
        AtomicLong submitted = new AtomicLong(0);
        AtomicLong completed = new AtomicLong(0);
        AtomicBoolean livenessOk = new AtomicBoolean(true);
        DocprocLivenessWatchdog watchdog = watchdog(submitted, completed, livenessOk);
        watchdog.sample(); // establish baseline while idle, as would happen in production before any stall begins

        submitted.incrementAndGet(); // one outstanding request, never completes
        for (int i = 0; i < SAMPLES_TO_TRIP - 1; i++)
            watchdog.sample();

        assertFalse(watchdog.isStalled());
        assertTrue(livenessOk.get());
    }

    @Test
    public void testBacklogWithNoProgressOverThresholdIsStalled() {
        AtomicLong submitted = new AtomicLong(0);
        AtomicLong completed = new AtomicLong(0);
        AtomicBoolean livenessOk = new AtomicBoolean(true);
        DocprocLivenessWatchdog watchdog = watchdog(submitted, completed, livenessOk);
        watchdog.sample(); // establish baseline while idle

        submitted.incrementAndGet(); // one outstanding request, never completes
        for (int i = 0; i < SAMPLES_TO_TRIP; i++)
            watchdog.sample();

        assertTrue(watchdog.isStalled());
        assertFalse(livenessOk.get());
    }

    @Test
    public void testRecoveryAfterProgressResumes() {
        AtomicLong submitted = new AtomicLong(0);
        AtomicLong completed = new AtomicLong(0);
        AtomicBoolean livenessOk = new AtomicBoolean(true);
        DocprocLivenessWatchdog watchdog = watchdog(submitted, completed, livenessOk);
        watchdog.sample(); // establish baseline while idle

        submitted.incrementAndGet();
        for (int i = 0; i < SAMPLES_TO_TRIP; i++)
            watchdog.sample();
        assertTrue(watchdog.isStalled());
        assertFalse(livenessOk.get());

        // Progress resumes and the outstanding request completes
        completed.incrementAndGet();
        for (int i = 0; i < SAMPLES_TO_TRIP; i++)
            watchdog.sample();

        assertFalse(watchdog.isStalled());
        assertTrue(livenessOk.get());
    }

    private static DocprocLivenessWatchdog watchdog(AtomicLong submitted, AtomicLong completed, AtomicBoolean livenessOk) {
        return new DocprocLivenessWatchdog(submitted::get, completed::get, livenessOk::set, SAMPLES_TO_TRIP);
    }

}

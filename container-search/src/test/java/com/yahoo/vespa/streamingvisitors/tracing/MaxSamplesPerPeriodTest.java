// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors.tracing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MaxSamplesPerPeriodTest {

    @Test
    void first_sample_in_period_returns_true() {
        var clock = MockUtils.mockedClockReturning(1000L);
        var sampler = new MaxSamplesPerPeriod(clock, 1000L, 1L);
        assertTrue(sampler.shouldSample());
    }

    @Test
    void samples_exceeding_period_count_return_false() {
        var clock = MockUtils.mockedClockReturning(1000L, 1100L, 1200L);
        var sampler = new MaxSamplesPerPeriod(clock, 1000L, 2L);
        assertTrue(sampler.shouldSample());
        assertTrue(sampler.shouldSample());
        assertFalse(sampler.shouldSample());
    }

    @Test
    void sample_in_new_period_returns_true() {
        var clock = MockUtils.mockedClockReturning(1000L, 1900L, 2000L, 2900L);
        var sampler = new MaxSamplesPerPeriod(clock, 1000L, 1L);
        assertTrue(sampler.shouldSample());
        assertFalse(sampler.shouldSample());
        assertTrue(sampler.shouldSample());
        assertFalse(sampler.shouldSample());
    }

}

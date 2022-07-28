// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors.tracing;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProbabilisticSampleRateTest {

    private static long ms2ns(long ms) {
        return TimeUnit.MILLISECONDS.toNanos(ms);
    }

    @Test
    void samples_are_rate_limited_per_second() {
        var clock = MockUtils.mockedClockReturning(ms2ns(10_000), ms2ns(10_500), ms2ns(10_500), ms2ns(10_501));
        var rng = MockUtils.mockedRandomReturning(0.1, 0.51, 0.49, 0.01);
        var sampler = new ProbabilisticSampleRate(clock, () -> rng, 1.0);
        // 1st invocation, 10 seconds (technically "infinity") since last sample. P = 1.0, sampled
        assertTrue(sampler.shouldSample());
        // 2nd invocation, 0.5 seconds since last sample. rng = 0.51 >= P = 0.5, not sampled
        assertFalse(sampler.shouldSample());
        // 3rd invocation, 0.5 seconds since last sample. rng = 0.49 < P = 0.5, sampled
        assertTrue(sampler.shouldSample());
        // 4th invocation, 0.001 seconds since last sample. rng = 0.01 >= P = 0.001, not sampled
        assertFalse(sampler.shouldSample());
    }

    @Test
    void zero_desired_sample_rate_returns_false() {
        var clock = MockUtils.mockedClockReturning(ms2ns(10_000));
        var rng = MockUtils.mockedRandomReturning(0.99999999); // [0, 1)
        var sampler = new ProbabilisticSampleRate(clock, () -> rng, 0.0);

        assertFalse(sampler.shouldSample());
    }

}

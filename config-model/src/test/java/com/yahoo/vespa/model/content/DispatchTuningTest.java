// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class DispatchTuningTest {

    @Test
    void requireThatAccessorWork() {
        DispatchTuning dispatch = new DispatchTuning.Builder()
                .setMaxHitsPerPartition(69)
                .setDispatchPolicy("round-robin")
                .setMinActiveDocsCoverage(12.5)
                .setTopKProbability(18.3)
                .build();
        assertEquals(69, dispatch.getMaxHitsPerPartition().intValue());
        assertEquals(12.5, dispatch.getMinActiveDocsCoverage(), 0.0);
        assertEquals(DispatchTuning.DispatchPolicy.ROUNDROBIN, dispatch.getDispatchPolicy());
        assertEquals(18.3, dispatch.getTopkProbability(), 0.0);
    }

    @Test
    void requireThatRandomDispatchWork() {
        DispatchTuning dispatch = new DispatchTuning.Builder()
                .setDispatchPolicy("random")
                .build();
        assertEquals(DispatchTuning.DispatchPolicy.ADAPTIVE, dispatch.getDispatchPolicy());
        assertNull(dispatch.getMinActiveDocsCoverage());
    }

    @Test
    void requireThatWeightedDispatchWork() {
        DispatchTuning dispatch = new DispatchTuning.Builder()
                .setDispatchPolicy("adaptive")
                .build();
        assertEquals(DispatchTuning.DispatchPolicy.ADAPTIVE, dispatch.getDispatchPolicy());
        assertNull(dispatch.getMinActiveDocsCoverage());
    }

    @Test
    void requireThatLatencyAmortizedOverRequestsDispatchWork() {
        DispatchTuning dispatch = new DispatchTuning.Builder()
                .setDispatchPolicy("latency-amortized-over-requests")
                .build();
        assertEquals(DispatchTuning.DispatchPolicy.LATENCY_AMORTIZED_OVER_REQUESTS, dispatch.getDispatchPolicy());
        assertNull(dispatch.getMinActiveDocsCoverage());
    }

    @Test
    void requireThatLatencyAmortizedOverTimeDispatchWork() {
        DispatchTuning dispatch = new DispatchTuning.Builder()
                .setDispatchPolicy("latency-amortized-over-time")
                .build();
        assertEquals(DispatchTuning.DispatchPolicy.LATENCY_AMORTIZED_OVER_TIME, dispatch.getDispatchPolicy());
        assertNull(dispatch.getMinActiveDocsCoverage());
    }

    @Test
    void requireThatBestOfRandom2DispatchWork() {
        DispatchTuning dispatch = new DispatchTuning.Builder()
                .setDispatchPolicy("best-of-random-2")
                .build();
        assertEquals(DispatchTuning.DispatchPolicy.BEST_OF_RANDOM_2, dispatch.getDispatchPolicy());
        assertNull(dispatch.getMinActiveDocsCoverage());
    }

    @Test
    void requireThatDefaultsAreNull() {
        DispatchTuning dispatch = new DispatchTuning.Builder().build();
        assertNull(dispatch.getMaxHitsPerPartition());
        assertNull(dispatch.getDispatchPolicy());
        assertNull(dispatch.getMinActiveDocsCoverage());
        assertNull(dispatch.getTopkProbability());
    }

}

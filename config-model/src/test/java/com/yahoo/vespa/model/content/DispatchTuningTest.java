// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class DispatchTuningTest {

    @Test
    public void requireThatAccessorWork() {
        DispatchTuning dispatch = new DispatchTuning.Builder()
                .setMaxHitsPerPartition(69)
                .setDispatchPolicy("round-robin")
                .setMinGroupCoverage(7.5)
                .setMinActiveDocsCoverage(12.5)
                .build();
        assertEquals(69, dispatch.maxHitsPerPartition().intValue());
        assertEquals(7.5, dispatch.minGroupCoverage().doubleValue(), 0.0);
        assertEquals(12.5, dispatch.minActiveDocsCoverage().doubleValue(), 0.0);
        assertTrue(DispatchTuning.DispatchPolicy.ROUNDROBIN == dispatch.dispatchPolicy());
    }
    @Test
    public void requireThatRandomDispatchWork() {
        DispatchTuning dispatch = new DispatchTuning.Builder()
                .setDispatchPolicy("random")
                .build();
        assertTrue(DispatchTuning.DispatchPolicy.ADAPTIVE == dispatch.dispatchPolicy());
        assertNull(dispatch.minGroupCoverage());
        assertNull(dispatch.minActiveDocsCoverage());
    }

    @Test
    public void requireThatWeightedDispatchWork() {
        DispatchTuning dispatch = new DispatchTuning.Builder()
                .setDispatchPolicy("adaptive")
                .build();
        assertTrue(DispatchTuning.DispatchPolicy.ADAPTIVE == dispatch.dispatchPolicy());
        assertNull(dispatch.minGroupCoverage());
        assertNull(dispatch.minActiveDocsCoverage());
    }

    @Test
    public void requireThatDefaultsAreNull() {
        DispatchTuning dispatch = new DispatchTuning.Builder().build();
        assertNull(dispatch.maxHitsPerPartition());
        assertNull(dispatch.dispatchPolicy());
        assertNull(dispatch.minActiveDocsCoverage());
        assertNull(dispatch.minGroupCoverage());
    }

}

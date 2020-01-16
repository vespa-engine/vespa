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
        assertEquals(69, dispatch.getMaxHitsPerPartition().intValue());
        assertEquals(7.5, dispatch.getMinGroupCoverage().doubleValue(), 0.0);
        assertEquals(12.5, dispatch.getMinActiveDocsCoverage().doubleValue(), 0.0);
        assertTrue(DispatchTuning.DispatchPolicy.ROUNDROBIN == dispatch.getDispatchPolicy());
    }
    @Test
    public void requireThatRandomDispatchWork() {
        DispatchTuning dispatch = new DispatchTuning.Builder()
                .setDispatchPolicy("random")
                .build();
        assertTrue(DispatchTuning.DispatchPolicy.ADAPTIVE == dispatch.getDispatchPolicy());
        assertNull(dispatch.getMinGroupCoverage());
        assertNull(dispatch.getMinActiveDocsCoverage());
    }

    @Test
    public void requireThatWeightedDispatchWork() {
        DispatchTuning dispatch = new DispatchTuning.Builder()
                .setDispatchPolicy("adaptive")
                .build();
        assertTrue(DispatchTuning.DispatchPolicy.ADAPTIVE == dispatch.getDispatchPolicy());
        assertNull(dispatch.getMinGroupCoverage());
        assertNull(dispatch.getMinActiveDocsCoverage());
    }

    @Test
    public void requireThatDefaultsAreNull() {
        DispatchTuning dispatch = new DispatchTuning.Builder().build();
        assertNull(dispatch.getMaxHitsPerPartition());
        assertNull(dispatch.getDispatchPolicy());
        assertNull(dispatch.getMinActiveDocsCoverage());
        assertNull(dispatch.getMinGroupCoverage());
    }

}

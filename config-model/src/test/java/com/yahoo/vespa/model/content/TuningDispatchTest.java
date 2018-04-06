// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class TuningDispatchTest {

    @Test
    public void requireThatAccessorWork() {
        TuningDispatch dispatch = new TuningDispatch.Builder()
                .setMaxHitsPerPartition(69)
                .setDispatchPolicy("round-robin")
                .setMinGroupCoverage(7.5)
                .setMinActiveDocsCoverage(12.5)
                .build();
        assertEquals(69, dispatch.getMaxHitsPerPartition().intValue());
        assertEquals(7.5, dispatch.getMinGroupCoverage().doubleValue(), 0.0);
        assertEquals(12.5, dispatch.getMinActiveDocsCoverage().doubleValue(), 0.0);
        assertTrue(TuningDispatch.DispatchPolicy.ROUNDROBIN == dispatch.getDispatchPolicy());
    }
    @Test
    public void requireThatRandomDispatchWork() {
        TuningDispatch dispatch = new TuningDispatch.Builder()
                .setDispatchPolicy("random")
                .build();
        assertTrue(TuningDispatch.DispatchPolicy.ADAPTIVE == dispatch.getDispatchPolicy());
        assertNull(dispatch.getMinGroupCoverage());
        assertNull(dispatch.getMinActiveDocsCoverage());
    }

    @Test
    public void requireThatWeightedDispatchWork() {
        TuningDispatch dispatch = new TuningDispatch.Builder()
                .setDispatchPolicy("adaptive")
                .build();
        assertTrue(TuningDispatch.DispatchPolicy.ADAPTIVE == dispatch.getDispatchPolicy());
        assertNull(dispatch.getMinGroupCoverage());
        assertNull(dispatch.getMinActiveDocsCoverage());
    }

    @Test
    public void requireThatDefaultsAreNull() {
        TuningDispatch dispatch = new TuningDispatch.Builder().build();
        assertNull(dispatch.getMaxHitsPerPartition());
        assertTrue(TuningDispatch.DispatchPolicy.ADAPTIVE == dispatch.getDispatchPolicy());
    }
}

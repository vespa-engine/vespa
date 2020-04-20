// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TopKEstimatorTest {
    @Test
    public void requireHitsAreEstimatedAccordingToPartitionsAndProbability() {
        TopKEstimator estimator = new TopKEstimator(30, 0.999);
        assertEquals(91.97368471911312, estimator.estimateExactK(200, 3), 0.0);
        assertEquals(92, estimator.estimateK(200, 3));
        assertEquals(37.96328109101396, estimator.estimateExactK(200, 10), 0.0);
        assertEquals(38, estimator.estimateK(200, 10));
        assertEquals(23.815737601023095, estimator.estimateExactK(200, 20), 0.0);
        assertEquals(24, estimator.estimateK(200, 20));

        assertEquals(37.96328109101396, estimator.estimateExactK(200, 10, 0.999), 0.0);
        assertEquals(38, estimator.estimateK(200, 10, 0.999));
        assertEquals(34.36212304875885, estimator.estimateExactK(200, 10, 0.99), 0.0);
        assertEquals(35, estimator.estimateK(200, 10, 0.99));
        assertEquals(41.44244358524574, estimator.estimateExactK(200, 10, 0.9999), 0.0);
        assertEquals(42, estimator.estimateK(200, 10, 0.9999));
        assertEquals(44.909040374464155, estimator.estimateExactK(200, 10, 0.99999), 0.0);
        assertEquals(45, estimator.estimateK(200, 10, 0.99999));
    }
}

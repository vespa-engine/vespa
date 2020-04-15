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
    }
}

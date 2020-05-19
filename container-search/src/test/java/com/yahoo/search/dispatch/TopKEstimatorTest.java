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
    @Test
    public void requireHitsAreEstimatedAccordingToPartitionsAndProbabilityForVaryingN_K200() {
        TopKEstimator estimator = new TopKEstimator(30, 0.99999);
        assertEquals(200, estimator.estimateExactK(200, 1), 0.0);
        assertEquals(200, estimator.estimateK(200, 1));
        assertEquals(137.4727798056239, estimator.estimateExactK(200, 2), 0.0);
        assertEquals(102.95409291533568, estimator.estimateExactK(200, 3), 0.0);
        assertEquals(44.909040374464155, estimator.estimateExactK(200, 10), 0.0);
        assertEquals(28.86025772029091, estimator.estimateExactK(200, 20), 0.0);
    }

    @Test
    public void requireHitsAreEstimatedAccordingToPartitionsAndProbabilityForVaryingN_K20() {
        TopKEstimator estimator = new TopKEstimator(30, 0.99999);
        assertEquals(20, estimator.estimateExactK(20, 1), 0.0);
        assertEquals(20, estimator.estimateK(20, 1));
        assertEquals(21.849933444373328, estimator.estimateExactK(20, 2), 0.0);
        assertEquals(18.14175840378403, estimator.estimateExactK(20, 3), 0.0);
        assertEquals(9.87693019124002, estimator.estimateExactK(20, 10), 0.0);
        assertEquals(6.964137165389415, estimator.estimateExactK(20, 20), 0.0);
    }

    @Test
    public void requireHitsAreEstimatedAccordingToPartitionsAndProbabilityForVaryingN_K10_Five9() {
        TopKEstimator estimator = new TopKEstimator(30, 0.99999);
        assertEquals(10, estimator.estimateExactK(10, 1), 0.0);
        assertEquals(10, estimator.estimateK(10, 1));
        assertEquals(13.379168295125641, estimator.estimateExactK(10, 2), 0.0);
        assertEquals(11.447448515386741, estimator.estimateExactK(10, 3), 0.0);
        assertEquals(6.569830753158866, estimator.estimateExactK(10, 10), 0.0);
        assertEquals(4.717281833573569, estimator.estimateExactK(10, 20), 0.0);
    }

    @Test
    public void requireHitsAreEstimatedAccordingToPartitionsAndProbabilityForVaryingN_K10_Four9() {
        TopKEstimator estimator = new TopKEstimator(30, 0.9999);
        assertEquals(10, estimator.estimateExactK(10, 1), 0.0);
        assertEquals(10, estimator.estimateK(10, 1));
        assertEquals(12.087323848369289, estimator.estimateExactK(10, 2), 0.0);
        assertEquals(10.230749855131009, estimator.estimateExactK(10, 3), 0.0);
        assertEquals(5.794676146031378, estimator.estimateExactK(10, 10), 0.0);
        assertEquals(4.152394782937266, estimator.estimateExactK(10, 20), 0.0);
    }

    @Test
    public void requireEstimatesAreRoundeUp() {
        TopKEstimator estimator = new TopKEstimator(30, 0.9999);
        assertEquals(5.794676146031378, estimator.estimateExactK(10, 10), 0.0);
        assertEquals(6, estimator.estimateK(10, 10));
    }

    @Test
    public void requireEstimatesAreCappedAtInputK() {
        TopKEstimator estimator = new TopKEstimator(30, 0.9999);
        assertEquals(12.087323848369289, estimator.estimateExactK(10, 2), 0.0);
        assertEquals(10, estimator.estimateK(10, 2));
    }

}

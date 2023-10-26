// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BiasEstimatorTest {

    @Test
    public void requireThatExactValueIsReturnedIfAvailable() {
        BiasEstimator biasEstimator = new BiasEstimator(10);
        // Index 0 in biasData/rawEstimateData
        assertEstimateEquals(737.1256, 738.1256, biasEstimator);
        // Index 10 in biasData/rawEstimateData
        assertEstimateEquals(612.1992, 868.1992, biasEstimator);
        // Index 199 (last) in biasData/rawEstimateData
        assertEstimateEquals(-9.81720000000041, 5084.1828, biasEstimator);
    }

    @Test
    public void requireThatBiasEstimatorHandlesAllValidPrecisions() {
        // Index 0 values for biasData/rawEstimateData for each precision
        double[][] testValuesForPrecision = new double[][] {
                {11, 10},
                {23, 22},
                {46, 45},
                {92, 91},
                {184.2152, 183.2152},
                {369, 368},
                {738.1256, 737.1256},
                {1477, 1476},
                {2954, 2953},
                {5908.5052, 5907.5052},
                {11817.475, 11816.475},
                {23635.0036, 23634.0036},
                {47271, 47270},
                {94542, 94541},
                {189084, 189083}
        };
        for (int p = 4; p <= 18; p++) {
            assertEstimateEquals(testValuesForPrecision[p - 4][1], testValuesForPrecision[p - 4][0], new BiasEstimator(p));
        }
    }

    @Test
    public void requireThatEdgeCasesAreCorrect() {
        BiasEstimator estimator = new BiasEstimator(10);
        // Test with a raw estimate less than first element of rawEstimateData
        assertEstimateEquals(737.1256, 7, estimator);
        // Test with a raw estimate larger than last element of rawEstimateData
        assertEstimateEquals(-9.81720000000041, 9001, estimator);
    }

    @Test
    public void requireThatLinearInterpolationIsCorrect() {
        BiasEstimator estimator = new BiasEstimator(10);
        double rawEstimate = (738.1256 + 750.4234) / 2; // average of two first elements
        double expectedBias = (737.1256 + 724.4234) / 2;
        assertEstimateEquals(expectedBias, rawEstimate, estimator);

        rawEstimate = 3 * 854.7864 / 4 + 868.1992 / 4; // weighted average of element 10 and 11
        expectedBias = 3 * 623.7864 / 4 + 612.1992 / 4;
        assertEstimateEquals(expectedBias, rawEstimate, estimator);
    }

    private static void assertEstimateEquals(double expected, double rawEstimate, BiasEstimator biasEstimator) {
        assertEquals(expected, biasEstimator.estimateBias(rawEstimate), 0.00000001);
    }
}

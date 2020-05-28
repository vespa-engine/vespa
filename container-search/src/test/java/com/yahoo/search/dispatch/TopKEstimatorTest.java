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

    @Test
    public void requireThatLargeKAreSane() {
        TopKEstimator estimator = new TopKEstimator(30, 0.9999);
        assertEquals(6, estimator.estimateK(10, 10));
        assertEquals(9, estimator.estimateK(20, 10));
        assertEquals(14, estimator.estimateK(40, 10));
        assertEquals(22, estimator.estimateK(80, 10));
        assertEquals(26, estimator.estimateK(100, 10));
        assertEquals(42, estimator.estimateK(200, 10));
        assertEquals(71, estimator.estimateK(400, 10));
        assertEquals(123, estimator.estimateK(800, 10));
        assertEquals(148, estimator.estimateK(1000, 10));
        assertEquals(268, estimator.estimateK(2000, 10));
        assertEquals(496, estimator.estimateK(4000, 10));
        assertEquals(936, estimator.estimateK(8000, 10));
        assertEquals(1152, estimator.estimateK(10000, 10));
        assertEquals(2215, estimator.estimateK(20000, 10));
        assertEquals(4304, estimator.estimateK(40000, 10));
        assertEquals(8429, estimator.estimateK(80000, 10));
        assertEquals(10480, estimator.estimateK(100000, 10));
        String expected =
                "Prob/Hits: 1.0000000000 0.9999000000 0.9999900000 0.9999990000 0.9999999000 0.9999999900 0.9999999990 0.9999999999\n" +
                "       10:       10.000        6.000        7.000        8.000        9.000       10.000       10.000       10.000\n" +
                "       20:       10.000        4.500        5.000        5.500        6.500        7.000        7.500        8.000\n" +
                "       40:       10.000        3.500        4.000        4.250        4.750        5.250        5.500        6.000\n" +
                "       80:       10.000        2.750        3.000        3.250        3.625        3.875        4.250        4.500\n" +
                "      100:       10.000        2.600        2.800        3.100        3.300        3.600        3.900        4.200\n" +
                "      200:       10.000        2.100        2.250        2.450        2.650        2.800        3.000        3.200\n" +
                "      400:       10.000        1.775        1.900        2.025        2.150        2.275        2.425        2.575\n" +
                "      800:       10.000        1.538        1.625        1.713        1.813        1.900        2.000        2.100\n" +
                "     1000:       10.000        1.480        1.560        1.640        1.720        1.810        1.890        1.990\n" +
                "     2000:       10.000        1.340        1.395        1.450        1.510        1.570        1.630        1.695\n" +
                "     4000:       10.000        1.240        1.280        1.320        1.360        1.403        1.445        1.493\n" +
                "     8000:       10.000        1.170        1.198        1.225        1.254        1.284        1.315        1.348\n" +
                "    10000:       10.000        1.152        1.177        1.202        1.227        1.254        1.282        1.311\n" +
                "    20000:       10.000        1.108        1.125        1.143        1.161        1.180        1.199        1.220\n" +
                "    40000:       10.000        1.076        1.088        1.101        1.114        1.127        1.141        1.156\n" +
                "    80000:       10.000        1.054        1.062        1.071        1.080        1.090        1.100        1.110\n" +
                "   100000:       10.000        1.048        1.056        1.064        1.072        1.080        1.089        1.098\n";
        assertEquals(expected, dumpProbability(10));
    }

    /**
     * This make a table showing how many more hits will be fetched as a factor of hits requested.
     * It shows how it varies with probability and hits requested for a given number of partitions.
     */
    private String dumpProbability(int numPartitions) {
        TopKEstimator estimator = new TopKEstimator(30, 0.9999);
        int [] K = {10, 20, 40, 80, 100, 200, 400, 800, 1000, 2000, 4000, 8000, 10000, 20000, 40000, 80000, 100000};
        double [] P = {1.0, 0.9999, 0.99999, 0.999999, 0.9999999, 0.99999999, 0.999999999, 0.9999999999};
        int n = numPartitions;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Prob/Hits:"));
        for (double p : P) {
            sb.append(String.format(" %1.10f", p));
        }
        sb.append("\n");
        for (int k : K) {
            sb.append(String.format("%9d:", k));
            for (double p : P) {
                sb.append(String.format("%13.3f", (double)(estimator.estimateK(k, n, p)*n) / k));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

}

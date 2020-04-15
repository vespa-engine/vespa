// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import org.apache.commons.math3.distribution.TDistribution;

/**
 * Use StudentT distribution and estimate how many hits you need from each partition
 * to to get the globally top-k documents with the desired probability
 * @author baldersheim
 */
public class TopKEstimator {
    private final TDistribution studentT;
    private final double defaultP;
    private final boolean estimate;

    private static boolean needEstimate(double p) {
        return (0.0 < p) && (p < 1.0);
    }
    public TopKEstimator(double freedom, double defaultProbability) {
        this.studentT = new TDistribution(null, freedom);
        defaultP = defaultProbability;
        estimate = needEstimate(defaultP);
    }
    double estimateExactK(double k, double n, double p) {
        double variance = k * 1/n * (1 - 1/n);
        double p_inverse = 1 - (1 - p)/n;
        return k/n + studentT.inverseCumulativeProbability(p_inverse) * Math.sqrt(variance);
    }
    double estimateExactK(double k, double n) {
        return estimateExactK(k, n, defaultP);
    }
    public int estimateK(int k, int n) {
        return (estimate && n > 1)
                ? (int)Math.ceil(estimateExactK(k, n, defaultP))
                : k;
    }
    public int estimateK(int k, int n, double p) {
        return (needEstimate(p) && (n > 1))
                ? (int)Math.ceil(estimateExactK(k, n, p))
                : k;
    }
}

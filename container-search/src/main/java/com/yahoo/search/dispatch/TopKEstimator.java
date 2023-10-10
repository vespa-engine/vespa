// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import org.apache.commons.math3.distribution.TDistribution;

/**
 * Use StudentT distribution and estimate how many hits you need from each partition
 * to to get the globally top-k documents with the desired probability
 *
 * @author baldersheim
 */
public class TopKEstimator {

    private final TDistribution studentT;
    private final double defaultP;
    private final boolean estimate;
    private final double skewFactor;
    private final double [] defaultCumulativeProbability;
    private final static int MIN_N = 2;

    private static boolean needEstimate(double p) {
        return (0.0 < p) && (p < 1.0);
    }

    TopKEstimator(double freedom, double defaultProbability) {
        this(freedom, defaultProbability, 0.0);
    }

    public TopKEstimator(double freedom, double defaultProbability, double skewFactor) {
        this.studentT = new TDistribution(null, freedom);
        defaultP = defaultProbability;
        estimate = needEstimate(defaultP);
        this.skewFactor = skewFactor;
        defaultCumulativeProbability = new double[64];
        for (int i=0; i < defaultCumulativeProbability.length; i++) {
            defaultCumulativeProbability[i] = computeCumulativeProbability(i+MIN_N, defaultP);
        }
    }

    private double inverseCumulativeProbability(int n, double p) {
        if (p == defaultP && (n >= MIN_N) && (n < defaultCumulativeProbability.length + MIN_N)) {
            return defaultCumulativeProbability[n - MIN_N];
        }
        return computeCumulativeProbability(n, p);
    }

    private double computeCumulativeProbability(int n, double p) {
        double p_inverse = 1 - (1 - p)/computeN(n);
        return studentT.inverseCumulativeProbability(p_inverse);
    }

    private double computeN(double n) {
        double p_max = (1 + skewFactor)/n;
        return Math.max(1, 1/p_max);
    }

    double estimateExactK(double k, int n_i, double p) {
        double n = computeN(n_i);
        double variance = k * 1/n * (1 - 1/n);
        return k/n + inverseCumulativeProbability(n_i, p) * Math.sqrt(variance);
    }

    double estimateExactK(double k, int n) {
        return estimateExactK(k, n, defaultP);
    }

    public int estimateK(int k, int n) {
        return (estimate && (n >= MIN_N))
                ? Math.min(k, (int)Math.ceil(estimateExactK(k, n, defaultP)))
                : k;
    }

    public int estimateK(int k, int n, double p) {
        return (needEstimate(p) && (n >= MIN_N))
                ? Math.min(k, (int)Math.ceil(estimateExactK(k, n, p)))
                : k;
    }
}


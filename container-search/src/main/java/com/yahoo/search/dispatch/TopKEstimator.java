package com.yahoo.search.dispatch;

import org.apache.commons.math3.distribution.TDistribution;

/**
 * Use StudentT distribution and estimate how many hits you need from each partition
 * to to get the globally top-k documents with the desired probability
 * @author baldersheim
 */
public class TopKEstimator {
    private final TDistribution studentT;
    private final double p;
    private final boolean estimate;

    public TopKEstimator(double freedom, double wantedprobability) {
        this.studentT = new TDistribution(null, freedom);
        p = wantedprobability;
        estimate = (0.0 < p) && (p < 1.0);
    }
    double estimateExactK(double k, double n) {
        double variance = k * 1/n * (1 - 1/n);
        double p_inverse = 1 - (1 - p)/n;
        return k/n + studentT.inverseCumulativeProbability(p_inverse) * Math.sqrt(variance);
    }
    public int estimateK(int k, int n) {
        return (estimate && n > 1)
                ? (int)Math.ceil(estimateExactK(k, n))
                : k;
    }
}

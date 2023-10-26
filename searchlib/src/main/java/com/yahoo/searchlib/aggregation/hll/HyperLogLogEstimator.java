// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

import com.google.common.base.Preconditions;

/**
 * Implementation of the result computation phase of the HyperLogLog algorithm.
 * Based on the pseudo code from: http://www.dmtcs.org/dmtcs-ojs/index.php/proceedings/article/viewArticle/914
 *
 * @author bjorncs
 */
public class HyperLogLogEstimator implements UniqueCountEstimator<Sketch<?>> {

    // Number of buckets in sketch.
    private final int nBuckets;
    // The bias estimator used to bias correct the raw estimate.
    private final BiasEstimator biasEstimator;
    // Linear counting threshold. Linear counting will only be used if raw estimate is equal or below this threshold.
    private final int linearCountingThreshold;
    // A bias correcting constant used in calculation of raw estimate.
    private final double alphaCoefficient;

    /**
     * Creates the estimator for a given precision. The resulting memory consumption is the exponential to the precision.
     *
     * @param precision The precision parameter as defined in HLL algorithm.
     */
    public HyperLogLogEstimator(int precision) {
        Preconditions.checkArgument(precision >= 4 && precision <= 18, "Invalid precision: %s.", precision);
        this.nBuckets = 1 << precision;
        this.biasEstimator = new BiasEstimator(precision);
        this.linearCountingThreshold = getLinearCountingThreshold(precision);
        this.alphaCoefficient = getAlphaCoefficient(nBuckets);
    }


    /**
     * Creates the estimator with the default precision ({@link HyperLogLog#DEFAULT_PRECISION}.
     */
    public HyperLogLogEstimator() {
        this(HyperLogLog.DEFAULT_PRECISION);
    }

    /**
     * Estimates the number of unique elements.
     *
     * @param sketch A sketch populated with values from the aggregation phase of HLL.
     * @return The estimated number of unique elements.
     */
    @Override
    public long estimateCount(Sketch<?> sketch) {
        if (sketch instanceof NormalSketch) {
            return estimateCount((NormalSketch) sketch);
        } else {
            return estimateCount((SparseSketch) sketch);
        }
    }

    // The sparse sketch contains a set of unique hash values. The size of this set is a good estimator as the
    // probability for hash collision is very low.
    private long estimateCount(SparseSketch sketch) {
        return sketch.size();
    }


    // Performs the result calculation phase of HLL. Note that the {@link NormalSketch}
    // precision must match the one supplied in the constructor.
    private long estimateCount(NormalSketch sketch) {
        Preconditions.checkArgument(sketch.size() == nBuckets,
                "Sketch has invalid size. Expected %s, actual %s.", nBuckets, sketch.size());
        double rawEstimate = calculateRawEstimate(sketch);
        if (shouldPerformBiasCorrection(rawEstimate)) {
            rawEstimate -= biasEstimator.estimateBias(rawEstimate);
        }

        // Use linear counting if sketch contains buckets with 0 value.
        int nZeroBuckets = countZeroBuckets(sketch);
        if (nZeroBuckets > 0) {
            double linearCountingEstimate = calculateLinearCountingEstimate(nZeroBuckets);
            if (linearCountingEstimate <= linearCountingThreshold) {
                rawEstimate = linearCountingEstimate;
            }
        }

        return Math.round(rawEstimate);
    }

    private double calculateLinearCountingEstimate(int nZeroBuckets) {
        return nBuckets * Math.log(nBuckets / (double) nZeroBuckets);
    }

    private boolean shouldPerformBiasCorrection(double rawEstimate) {
        return rawEstimate <= 5 * nBuckets;
    }

    private double calculateRawEstimate(NormalSketch sketch) {
        double indicator = calculateIndicator(sketch);
        return alphaCoefficient * nBuckets * nBuckets * indicator;
    }

     // Calculates the raw indicator, summing up the probabilities for each bucket.
     // indicator == 1 / sum(2^(-S[i]) where i = 0 to n
    private static double calculateIndicator(NormalSketch sketch) {
        double sum = 0;
        for (byte prefixLength : sketch.data()) {
            sum += Math.pow(2, -prefixLength);
        }
        return 1 / sum;
    }

    private static int countZeroBuckets(NormalSketch sketch) {
        int nZeroBuckets = 0;
        for (byte prefixLength : sketch.data()) {
            if (prefixLength == 0) {
                ++nZeroBuckets;
            }
        }
        return nZeroBuckets;
    }

    // Empirically determined values from Google HLL++ paper. Decides whether to use linear counting instead of raw HLL estimate.
    private static int getLinearCountingThreshold(int precision) {
        switch (precision) {
            case 4:
                return 10;
            case 5:
                return 20;
            case 6:
                return 40;
            case 7:
                return 80;
            case 8:
                return 220;
            case 9:
                return 400;
            case 10:
                return 900;
            case 11:
                return 1800;
            case 12:
                return 3100;
            case 13:
                return 6500;
            case 14:
                return 11500;
            case 15:
                return 22000;
            case 16:
                return 50000;
            case 17:
                return 120000;
            case 18:
                return 350000;
            default:
                // Unreachable code.
                throw new RuntimeException();
        }
    }

    private static double getAlphaCoefficient(int nBuckets) {
        switch (nBuckets) {
            case 16:
                return 0.673;
            case 32:
                return 0.697;
            case 64:
                return 0.709;
            default: /* nBuckets >= 128 */
                return 0.7213 / (1 + 1.079 / nBuckets);
        }
    }
}

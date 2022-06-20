// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * This benchmarks performs a series of unique counting tests to analyse the HyperLogLog accuracy.
 */
public class HyperLogLogPrecisionBenchmark {

    private static final int MAX_VAL = 256_000;
    private static final int MAX_ITERATION = 1000;

    private static final XXHash32 hashGenerator = XXHashFactory.safeInstance().hash32();
    private static final HyperLogLogEstimator estimator = new HyperLogLogEstimator();
    private static final Random random = new Random(424242);


    public static void main(String[] args) {
        System.out.println("Unique count; Average estimated unique count; Normalized standard error; Standard error; Min; Max");
        for (int val = 1; val <= MAX_VAL; val *= 2) {
            List<Long> samples = new ArrayList<>();
            long sumEstimates = 0;
            for (int iteration = 0; iteration < MAX_ITERATION; iteration++) {
                long sample = estimateUniqueCount(val);
                samples.add(sample);
                sumEstimates += sample;
            }
            double average = sumEstimates / (double) MAX_ITERATION;
            long min = samples.stream().min(Long::compare).get();
            long max = samples.stream().max(Long::compare).get();
            double standardDeviation = getStandardDeviation(samples, average);
            System.out.printf("%d; %.2f; %.4f; %.4f; %d; %d\n", val, average, standardDeviation / average, standardDeviation, min, max);
        }
    }

    private static double getStandardDeviation(List<Long> samples, double average) {
        double sumSquared = 0;
        for (long sample : samples) {
            sumSquared += Math.pow(sample - average, 2);
        }
        return Math.sqrt(sumSquared / samples.size());
    }

    private static long estimateUniqueCount(int nValues) {
        SparseSketch sparse = new SparseSketch();
        while (sparse.size() < nValues) {
            sparse.aggregate(makeHash(random.nextInt()));
        }
        if (sparse.size() > HyperLogLog.SPARSE_SKETCH_CONVERSION_THRESHOLD) {
            NormalSketch normal = new NormalSketch();
            normal.aggregate(sparse.data());
            return estimator.estimateCount(normal);
        } else {
            return estimator.estimateCount(sparse);
        }
    }

    private static int makeHash(int value) {
        final int seed = 1333337;
        byte[] bytes = ByteBuffer.allocate(4).putInt(value).array();
        return hashGenerator.hash(bytes, 0, 4, seed);
    }
}

// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Class that has a method for finding next maxInFlight.
 * @author dybis
 */
public class ThrottlePolicy {

    public static final double SMALL_DIFFERENCE_IN_SUCCESSES_RATIO = 0.15;
    private static final double MINIMUM_DIFFERENCE = 0.05;

    /**
     * Generate nex in-flight value for throttling.
     * @param maxPerformanceChange This value limit the dynamics of the algorithm.
     * @param numOk number of success in last phase
     * @param previousNumOk number of success in previous (before last) phase.
     * @param previousMaxInFlight number of max-in-flight in previous (before last) phase.
     * @param maxInFlightNow number of max-in-flight in last phase.
     * @param messagesQueued if any messages where queued.
     * @return The new value to be used for max-in-flight (should be cropped externally to fit max/min values).
     */
    public int calcNewMaxInFlight(
            final double maxPerformanceChange,
            final int numOk,
            final int previousNumOk,
            final int previousMaxInFlight,
            final int maxInFlightNow,
            final boolean messagesQueued) {

        double difference = calculateRuleBasedDifference(
                maxPerformanceChange, numOk, previousNumOk, previousMaxInFlight, maxInFlightNow);
        boolean previousRunWasBetter = numOk < previousNumOk;
        boolean previousRunHadLessInFlight = previousMaxInFlight < maxInFlightNow;


        int delta;
        if (previousRunWasBetter == previousRunHadLessInFlight) {
            delta = (int) (-1.1 * difference * maxInFlightNow);
        } else {
            delta = (int) (difference * maxInFlightNow);
        }

        // We don't want the same size since we need different sizes for algorithm to adjust.
        if (abs(delta) < 2) {
            delta = -3;
        }
        // We never used all permits in previous run, no reason to grow more, we should rather reduce permits.
        if (!messagesQueued && delta > 0) {
            delta = -2;
        }
        return maxInFlightNow + delta;
    }

    private static double calculateRuleBasedDifference(
            final double maxPerformanceChange,
            final double numOk,
            final double previousNumOk,
            final double previousMaxInFlight,
            final double maxInFlightNow) {
        double difference = min(
                maxPerformanceChange,
                abs((numOk - previousNumOk) / safeDenominator(previousNumOk)));

        if (abs(previousMaxInFlight - maxInFlightNow) / safeDenominator(min(previousMaxInFlight, maxInFlightNow))
                < SMALL_DIFFERENCE_IN_SUCCESSES_RATIO) {
            difference = min(difference, 0.2);
        }

        // We want some changes so we can track performance as a result of different throttling.
        return max(difference, MINIMUM_DIFFERENCE);
    }

    private static double safeDenominator(double x) {
        return x == 0.0 ? 1.0 : x;
    }
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

/**
 * A running set of samples for a Value instance. It is
 * used only in a very specific context between the sampling threads (each instance
 * is only used by on sampling thread) and the single logging thread.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
final class SampleSet {
    Sampling values;
    final Limits histogramLimits;
    private boolean isRegisteredForLogging = false;

    SampleSet(Limits histogramLimits) {
        this.histogramLimits = histogramLimits;
        values = new Sampling(0.0d, 0L, 0.0d, 0.0d, histogramLimits);
    }

    static final class Sampling {
        final double sum;
        final long insertions;
        final double max;
        final double min;
        final Histogram histogram;

        Sampling(double sum, long insertions, double max, double min, Limits histogramLimits) {
            if (histogramLimits != null) {
                this.histogram = new Histogram(histogramLimits);
            } else {
                this.histogram = null;
            }
            this.sum = sum;
            this.insertions = insertions;
            this.max = max;
            this.min = min;
        }

        Sampling(double sum, long insertions, double max, double min, Histogram histogram) {
            this.histogram = histogram;
            this.sum = sum;
            this.insertions = insertions;
            this.max = max;
            this.min = min;
        }
    }


    private Sampling createSampling(double x, Sampling previous) {
        double sum = previous.sum;
        long insertions = previous.insertions;
        double max = previous.max;
        double min = previous.min;

        sum += x;
        if (insertions == 0) {
            max = x;
            min = x;
        } else {
            max = Math.max(x, max);
            min = Math.min(x, min);
        }
        insertions++;
        return new Sampling(sum, insertions, max, min, previous.histogram);
    }

    /**
     * Insert x, do all pertinent operations. (Update histogram, update
     * insertion count for calculating mean, etc.)
     *
     * @return whether this is registered for logging
     */
    synchronized boolean put(double x) {
        Sampling previous = values;
        Histogram histogram = previous.histogram;
        if (histogram != null) {
            histogram.put(new double[] {x});
        }
        values = createSampling(x, previous);
        return isRegisteredForLogging;
    }

    /**
     * Get state and reset it.
     */
    synchronized Sampling getAndReset() {
        Sampling previous = values;
        values = new Sampling(0.0d, 0L, 0.0d, 0.0d, histogramLimits);
        setRegisteredForLogging(false);
        return previous;
    }

    // Setting this state is protected by SampleDirectory.directoryLock. It is
    // either set from the logging thread (protected by directoryLock and using
    // "this" as a memory barrier through getAndReset()), or from the sampling
    // thread protected by directoryLock and without a mem barrier.
    void setRegisteredForLogging(boolean isRegisteredForLogging) {
        this.isRegisteredForLogging = isRegisteredForLogging;
    }

}

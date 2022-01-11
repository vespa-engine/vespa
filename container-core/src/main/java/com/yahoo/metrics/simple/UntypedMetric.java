// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import com.yahoo.api.annotations.Beta;
import org.HdrHistogram.DoubleHistogram;

import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A gauge or a counter or... who knows? The class for storing a metric when the
 * metric has not been declared.
 *
 * @author Steinar Knutsen
 */
public class UntypedMetric {

    private static final Logger log = Logger.getLogger(UntypedMetric.class.getName());

    private long count = 0L;
    private double current = 0.0d;
    private double max;
    private double min;
    private double sum;
    private AssumedType outputFormat = AssumedType.NONE;
    private final DoubleHistogram histogram;
    private final MetricSettings metricSettings;

    public enum AssumedType { NONE, GAUGE, COUNTER };

    UntypedMetric(MetricSettings metricSettings) {
        this.metricSettings = metricSettings;
        if (metricSettings == null || !metricSettings.isHistogram()) {
            histogram = null;
        } else {
            histogram = new DoubleHistogram(metricSettings.getSignificantdigits());
        }
    }

    void add(Number x) {
        outputFormat = AssumedType.COUNTER;
        count += x.longValue();
    }

    void put(Number x) {
        outputFormat = AssumedType.GAUGE;
        current = x.doubleValue();
        if (histogram != null) {
            histogram.recordValue(current);
        }
        if (count > 0) {
            max = Math.max(current, max);
            min = Math.min(current, min);
            sum += current;
        } else {
            max = current;
            min = current;
            sum = current;
        }
        ++count;
    }

    UntypedMetric pruneData() {
        UntypedMetric pruned = new UntypedMetric(null);
        pruned.outputFormat = this.outputFormat;
        pruned.current = this.current;
        return pruned;
    }

    void merge(UntypedMetric other, boolean otherIsNewer) throws IllegalArgumentException {
        if (outputFormat == AssumedType.NONE) {
            outputFormat = other.outputFormat;
        }
        if (outputFormat != other.outputFormat) {
            throw new IllegalArgumentException("Mismatching output formats: " + outputFormat + " and " + other.outputFormat + ".");
        }
        if (count > 0) {
            if (other.count > 0) {
                max = Math.max(other.max, max);
                min = Math.min(other.min, min);
                if (otherIsNewer) {
                    current = other.current;
                }
            }
        } else {
            max = other.max;
            min = other.min;
            current = other.current;
        }
        count += other.count;
        sum += other.sum;
        if (histogram != null) {
            // some config scenarios may lead to differing histogram settings,
            // so doing this defensively
            if (other.histogram != null) {
                try {
                    histogram.add(other.histogram);
                } catch (ArrayIndexOutOfBoundsException e) {
                    log.log(Level.WARNING, "Had trouble merging histograms: " + e.getMessage());
                }
            }
        }
    }

    public boolean isCounter() { return outputFormat == AssumedType.COUNTER; }

    public long getCount() { return count; }
    public double getLast() { return current; }
    public double getMax() { return max; }
    public double getMin() { return min; }
    public double getSum() { return sum; }

    MetricSettings getMetricDefinition() {
        return metricSettings;
    }

    @Beta
    public Histogram getHistogram() {
        return histogram != null ? new Histogram(histogram) : null;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(this.getClass().getName()).append(": ");
        buf.append("outputFormat=").append(outputFormat).append(", ");
        if (count > 0 && outputFormat == AssumedType.GAUGE) {
            buf.append("max=").append(max).append(", ");
            buf.append("min=").append(min).append(", ");
            buf.append("sum=").append(sum).append(", ");
        }
        if (histogram != null) {
            buf.append("histogram=").append(histogram).append(", ");
        }
        if (metricSettings != null) {
            buf.append("metricSettings=").append(metricSettings).append(", ");
        }
        buf.append("current=").append(current).append(", ");
        buf.append("count=").append(count);
        return buf.toString();
    }

    @Beta
    public static class Histogram {
        private final DoubleHistogram hdrHistogram;

        private Histogram(DoubleHistogram hdrHistogram) { this.hdrHistogram = hdrHistogram; }

        public double getValueAtPercentile(double percentile) { return hdrHistogram.getValueAtPercentile(percentile); }

        public void outputPercentileDistribution(PrintStream printStream, int percentileTicksPerHalfDistance,
                                                 Double outputValueUnitScalingRatio, boolean useCsvFormat) {
            hdrHistogram.outputPercentileDistribution(
                    printStream, percentileTicksPerHalfDistance, outputValueUnitScalingRatio, useCsvFormat);
        }
    }

}

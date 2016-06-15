// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import java.util.logging.Logger;

import org.HdrHistogram.DoubleHistogram;

import com.yahoo.log.LogLevel;

/**
 * A gauge or a counter or... who knows? The class for storing a metric when the
 * metric has not been declared.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
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
        count += other.count;
        if (otherIsNewer) {
            current = other.current;
        }
        max = Math.max(other.max, max);
        min = Math.min(other.min, min);
        sum += other.sum;
        if (histogram != null) {
            // some config scenarios may lead to differing histogram settings,
            // so doing this defensively
            if (other.histogram != null) {
                try {
                    histogram.add(other.histogram);
                } catch (ArrayIndexOutOfBoundsException e) {
                    log.log(LogLevel.WARNING, "Had trouble merging histograms: " + e.getMessage());
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

    public DoubleHistogram getHistogram() {
        return histogram;
    }
}

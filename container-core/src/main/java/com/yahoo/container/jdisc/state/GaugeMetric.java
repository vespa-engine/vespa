// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import java.util.List;
import java.util.Optional;

import com.yahoo.collections.Tuple2;

/**
 * A metric which contains a gauge value, i.e a value which represents the magnitude of something
 * measured at a point in time. This metric value contains some additional information about the distribution
 * of this gauge value in the time interval this metric is for.
 *
 * @author Simon Thoresen Hult
 */
public final class GaugeMetric extends MetricValue {

    private double last;
    private double max;
    private double min;
    private double sum;
    private long count;
    private final Optional<List<Tuple2<String, Double>>> percentiles;

    private GaugeMetric(double last, double max, double min, double sum, long count, Optional<List<Tuple2<String, Double>>> percentiles) {
        this.last = last;
        this.max = max;
        this.min = min;
        this.sum = sum;
        this.count = count;
        this.percentiles = percentiles;
    }

    @Override
    void add(Number val) {
        double dval = val.doubleValue();
        last = dval;
        if (dval > max) {
            max = dval;
        }
        if (dval < min) {
            min = dval;
        }
        sum += dval;
        ++count;
    }

    @Override
    void add(MetricValue val) {
        GaugeMetric rhs = (GaugeMetric)val;
        last = rhs.last;
        if (rhs.max > max) {
            max = rhs.max;
        }
        if (rhs.min < min) {
            min = rhs.min;
        }
        sum += rhs.sum;
        count += rhs.count;
    }

    /**
     * Returns the average reading of this value in the time interval, or--if no
     * value has been set within this period--the value of 'last' from the
     * most recent interval where this metric was set.
     */
    public double getAverage() {
        return count != 0 ? (sum / count) : last;
    }

    /**
     * Returns the most recent assignment of this metric in the time interval,
     * or--if no value has been set within this period--the value of 'last'
     * from the most recent interval where this metric was set.
     */
    public double getLast() { return last; }

    /**
     * Returns the max value of this metric in the time interval, or--if no
     * value has been set within this period--the value of 'last' from the
     * most recent interval where this metric was set.
     */
    public double getMax() {
        return (count == 0) ? last : max;
    }

    /**
     * Returns the min value of this metric in the time interval, or--if no
     * value has been set within this period--the value of 'last' from the
     * most recent interval where this metric was set.
     */
    public double getMin() {
        return (count == 0) ? last : min;
    }

    /** Returns the sum of all assignments of this metric in the time interval */
    public double getSum() {
        return sum;
    }

    /** Returns the number of assignments of this value in the time interval */
    public long getCount() {
        return count;
    }

    /** Returns the 95th percentile value for this time interval */
    public Optional<List<Tuple2<String, Double>>> getPercentiles() {
        return percentiles;
    }

    /**
     * Create a partial clone of this gauge where the value of 'last' is
     * carried over to the new gauge with all other fields left at defaults
     * (0 for count and sum, biggest possible double for min, smallest possible
     * double for max). Note that since count is 0, these extreme values will
     * never be output from the min/max getters as these will return 'last'
     * in this case.
     * @return A new gauge instance
     */
    public GaugeMetric newWithPreservedLastValue() {
        // min/max set to enforce update of these values on first call to add()
        return new GaugeMetric(last, Double.MIN_VALUE, Double.MAX_VALUE, 0, 0, Optional.empty());
    }

    public static GaugeMetric newSingleValue(Number val) {
        double dval = val.doubleValue();
        return new GaugeMetric(dval, dval, dval, dval, 1, Optional.empty());
    }

    public static GaugeMetric newInstance(double last, double max, double min, double sum, long count) {
        return new GaugeMetric(last, max, min, sum, count, Optional.empty());
    }

    public static GaugeMetric newInstance(double last, double max, double min, double sum, long count, Optional<List<Tuple2<String, Double>>> percentiles) {
        return new GaugeMetric(last, max, min, sum, count, percentiles);
    }

}

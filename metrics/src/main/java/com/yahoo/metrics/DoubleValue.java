// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import com.yahoo.metrics.util.ValueType;

import java.util.Collection;
import java.util.Locale;

public class DoubleValue implements ValueMetric.Value<Double> {
    private int count;
    private double min, max, last;
    private double total;

    public DoubleValue() {
        count = 0;
        min = Double.POSITIVE_INFINITY;
        max = Double.NEGATIVE_INFINITY;
        last = 0;
        total = 0;
    }

    public String toString() {
        return "(count " + count + ", min " + min + ", max " + max + ", last " + last + ", total " + total + ")";
    }

    public void add(Double v) {
        count = count + 1;
        total = total + v;
        min = Math.min(min, v);
        max = Math.max(max, v);
        last = v;
    }

    public void join(ValueMetric.Value<Double> v2, boolean createAverageOnJoin) {
        //StringBuffer sb = new StringBuffer();
        //sb.append("Adding " + this + " to " + v2);
        if (createAverageOnJoin) {
            count += v2.getCount();
            total += v2.getTotal();
            last = v2.getLast();

        } else {
            double totalAverage = getAverage() + v2.getAverage();
            count += v2.getCount();
            total = totalAverage * count;
            last += v2.getLast();
        }
        min = Math.min(min, v2.getMin());
        max = Math.max(max, v2.getMax());
        //sb.append(" and got " + this);
        //System.err.println(sb.toString());
    }

    public void add(ValueType other) {
        DoubleValue dv = (DoubleValue) other;
        count = count + dv.count;
        total = total + dv.total;
        min = Math.min(min, dv.min);
        max = Math.max(max, dv.max);
        last = dv.last;
    }

    public ValueType join(Collection<ValueType> sources, JoinBehavior joinBehavior) {
        DoubleValue result = new DoubleValue();
        for (ValueType t : sources) {
            DoubleValue dv = (DoubleValue) t;
            result.count = result.count + dv.count;
            result.total = result.total + dv.total;
            result.min = Math.min(result.min, dv.min);
            result.max = Math.max(result.max, dv.max);
            result.last += dv.last;
        }
        if (joinBehavior == JoinBehavior.AVERAGE_ON_JOIN) {
            result.last /= sources.size();
        } else {
            result.total *= sources.size();
        }
        return result;
    }

    public boolean overflow(ValueMetric.Value<Double> v2) {
        if (count > (count + v2.getCount())) {
            return true;
        }
        if (v2.getTotal() > 0 && getTotal() > getTotal() + v2.getTotal()) {
            return true;
        }
        if (v2.getTotal() < 0 && getTotal() < getTotal() + v2.getTotal()) {
            return true;
        }

        return false;
    }

    public int getCount() { return count; }
    public Double getMin() { return (count > 0) ? min : 0; }
    public Double getMax() { return (count > 0) ? max : 0; }
    public Double getLast() { return last; }
    public Double getTotal() { return total; }

    public Double getAverage() {
        if (count == 0) {
            return 0.0;
        }

        return total / count;
    }

    public String valueToString(Double val) {
        if (val == Double.MIN_VALUE || val == Double.MAX_VALUE) {
            return "0.00";
        }

        return String.format(Locale.US, "%.2f", val);
    }

    public DoubleValue clone() {
        try{
            return (DoubleValue) super.clone();
        } catch (CloneNotSupportedException e) { return null; }
    }

    public ValueMetric.Value<Double> copyObject() {
        return clone();
    }

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import com.yahoo.metrics.util.ValueType;

import java.util.Collection;

/**
 * @author thomasg
 */
public class LongValue
    implements ValueMetric.Value<Long>
{
    private int count;
    private long min, max, last;
    private long total;

    public LongValue() {
        count = 0;
        min = Long.MAX_VALUE;
        max = Long.MIN_VALUE;
        last = 0;
        total = 0;
    }

    @Override
    public void add(Long v) {
        LongValue val = this;
        val.count = count + 1;
        val.total = total + v;
        val.min = Math.min(min, v);
        val.max = Math.max(max, v);
        val.last = v;
    }

    @Override
    public void join(ValueMetric.Value<Long> v2, boolean createAverageOnJoin) {
        LongValue value = this;

        if (createAverageOnJoin) {
            value.count = count + v2.getCount();
            value.total = total + v2.getTotal();
            value.last = v2.getLast();
        } else {
            double totalAverage = getAverage() + v2.getAverage();
            value.count = count + v2.getCount();
            value.total = (long) (totalAverage * value.count); // Total is "wrong" I guess.
            value.last = last + v2.getLast();
        }

        value.min = Math.min(min, v2.getMin());
        value.max = Math.max(max, v2.getMax());
    }

    @Override
    public void add(ValueType other) {
        LongValue dv = (LongValue) other;
        count = count + dv.count;
        total = total + dv.total;
        min = Math.min(min, dv.min);
        max = Math.max(max, dv.max);
        last = dv.last;
    }

    @Override
    public ValueType join(Collection<ValueType> sources, JoinBehavior joinBehavior) {
        LongValue result = new LongValue();
        for (ValueType t : sources) {
            LongValue dv = (LongValue) t;
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

    @Override
    public boolean overflow(ValueMetric.Value<Long> v2) {
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

    @Override
    public int getCount() { return count; }

    @Override
    public Long getMin() { return (count > 0) ? min : 0; }

    @Override
    public Long getMax() { return (count > 0) ? max : 0; }

    @Override
    public Long getLast() { return last; }

    @Override
    public Long getTotal() { return total; }

    @Override
    public Double getAverage() {
        if (count == 0) {
            return 0.0;
        }
        return ((double) total) / count;
    }

    @Override
    public String valueToString(Long val) {
        if (val == Long.MIN_VALUE || val == Long.MAX_VALUE) {
            return valueToString((long)0);
        }

        return val.toString();
    }

    @Override
    public LongValue clone() {
        try{
            return (LongValue) super.clone();
        } catch (CloneNotSupportedException e) { return null; }
    }

    @Override
    public ValueMetric.Value<Long> copyObject() {
        return clone();
    }
}

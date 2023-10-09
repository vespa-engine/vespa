// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

/**
 * A metric which is counting an accumulative value
 *
 * @author Simon Thoresen Hult
 */
public final class CountMetric extends MetricValue {

    private long count;

    private CountMetric(long count) {
        this.count = count;
    }

    @Override
    void add(Number num) {
        this.count += num.longValue();
    }

    @Override
    void add(MetricValue value) {
        CountMetric rhs = (CountMetric)value;
        this.count += rhs.count;
    }

    /** Returns the accumulated count of this metric in the time interval */
    public long getCount() {
        return count;
    }

    public static CountMetric newSingleValue(Number val) {
        long lval = val.longValue();
        return new CountMetric(lval);
    }

    public static CountMetric newInstance(long val) {
        return new CountMetric(val);
    }

}

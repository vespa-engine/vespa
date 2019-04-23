// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import com.yahoo.log.LogLevel;
import com.yahoo.metrics.util.HasCopy;
import com.yahoo.metrics.util.MetricValueKeeper;
import com.yahoo.metrics.util.SimpleMetricValueKeeper;
import com.yahoo.metrics.util.ThreadLocalDirectoryValueKeeper;
import com.yahoo.metrics.util.ValueType;
import com.yahoo.text.XMLWriter;
import com.yahoo.text.Utf8String;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * A metric that represents the value of something.
 */
public class ValueMetric<N extends Number>
    extends Metric
{
    private static Logger log = Logger.getLogger(ValueMetric.class.getName());
    private static AtomicBoolean hasWarnedOnNonFinite = new AtomicBoolean(false);

    public interface Value<N extends Number> extends ValueType, HasCopy<Value<N>> {
        void add(N v);
        void join(Value<N> v2, boolean createAverageOnJoin);
        boolean overflow(Value<N> v2);

        int getCount();
        N getMin();
        N getMax();
        N getLast();
        N getTotal();
        Double getAverage();

        String valueToString(N value);
    }

    public boolean hasFlag(int flag) { return (flags & flag) != 0; }
    public ValueMetric<N> setFlag(int flag) { flags |= flag; return this; }

    MetricValueKeeper<Value<N>> values;
    int flags = 0;

    static int AVERAGE_METRIC = 1;
    static int CREATE_AVERAGE_ON_JOIN = 2;
    static int UNSET_ON_ZERO_VALUE = 4;
    static int LOG_IF_UNSET = 8;

    boolean isAverageMetric() { return hasFlag(AVERAGE_METRIC); }
    boolean doCreateAverageOnJoin() { return hasFlag(CREATE_AVERAGE_ON_JOIN); }
    boolean isUnsetOnZeroValue() { return hasFlag(UNSET_ON_ZERO_VALUE); }
    boolean doLogIfUnset() { return hasFlag(LOG_IF_UNSET); }
    JoinBehavior getJoinBehavior() {
        return hasFlag(CREATE_AVERAGE_ON_JOIN) ? JoinBehavior.AVERAGE_ON_JOIN
                                               : JoinBehavior.SUM_ON_JOIN;
    }

    public ValueMetric<N> averageMetric() { return setFlag(AVERAGE_METRIC); }
    public ValueMetric<N> createAverageOnJoin() { return setFlag(CREATE_AVERAGE_ON_JOIN); }
    public ValueMetric<N> unsetOnZeroValue() { return setFlag(UNSET_ON_ZERO_VALUE); }
    public ValueMetric<N> logIfUnset() { return setFlag(LOG_IF_UNSET); }

    public ValueMetric(String name, String tags, String description, MetricSet owner) {
        super(name, tags, description, owner);
        //values = new MetricValueSet();
        values = new ThreadLocalDirectoryValueKeeper<>();
    }

    public ValueMetric(ValueMetric<N> other, CopyType copyType, MetricSet owner) {
        super(other, owner);
        if (copyType == CopyType.INACTIVE || other.values instanceof SimpleMetricValueKeeper) {
            values = new SimpleMetricValueKeeper<>();
            values.set(other.values.get(getJoinBehavior()));
        } else {
            //values = new MetricValueSet((MetricValueSet) other.values, ((MetricValueSet) other.values).size());
            values = new ThreadLocalDirectoryValueKeeper<>(other.values);
        }
        this.flags = other.flags;
    }

    private void logNonFiniteValueWarning() {
        if (!hasWarnedOnNonFinite.getAndSet(true)) {
            log.log(LogLevel.WARNING,
                    "Metric '" + getPath() + "' attempted updated with a value that is NaN or " +
                    "Infinity; update ignored! No further warnings will be printed for " +
                    "such updates on any metrics, but they can be observed with debug " +
                    "logging enabled on component " + log.getName());
        } else if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG,
                    "Metric '" + getPath() + "' attempted updated with a value that is NaN or " +
                    "Infinity; update ignored!");
        }
    }

    public void addValue(N v) {
        if (v == null) throw new NullPointerException("Cannot have null value");
        if (v instanceof Long) {
            LongValue lv = new LongValue();
            lv.add((Long) v);
            values.add((Value<N>) lv);
        } else {
            Double d = (Double)v;
            if (d.isNaN() || d.isInfinite()) {
                logNonFiniteValueWarning();
                return;
            }
            DoubleValue dv = new DoubleValue();
            dv.add(d);
            values.add((Value<N>) dv);
        }
    }

    private Value<N> getValues() {
        return values.get(getJoinBehavior());
    }

    @Override
    public void addToSnapshot(Metric snapshotMetric) {
        Value<N> v = getValues();
        if (v != null) ((ValueMetric<N>)snapshotMetric).join(v, true);
    }

    @Override
    void addToPart(Metric m) {
        Value<N> v = getValues();
        if (v != null) ((ValueMetric<N>) m).join(v, doCreateAverageOnJoin());
    }

    public void join(Value<N> v2, boolean createAverageOnJoin) {
        Value<N> tmpVals = getValues();
        if (tmpVals == null) {
            if (v2 instanceof LongValue) {
                tmpVals = (Value<N>) new LongValue();
            } else {
                tmpVals = (Value<N>) new DoubleValue();
            }
        }
        if (tmpVals.overflow(v2)) {
            this.values.reset();
            log.fine("Metric " + getPath() + " overflowed, resetting it.");
            return;
        }
        if (tmpVals.getCount() == 0) {
            tmpVals = v2;
        } else if (v2.getCount() == 0) {
            // Do nothing
        } else {
            tmpVals.join(v2, createAverageOnJoin);
        }
        this.values.set(tmpVals);
    }

    public static final Utf8String TAG_AVG  = new Utf8String("average");
    public static final Utf8String TAG_LAST = new Utf8String("last");
    public static final Utf8String TAG_MIN  = new Utf8String("min");
    public static final Utf8String TAG_MAX  = new Utf8String("max");
    public static final Utf8String TAG_CNT  = new Utf8String("count");
    public static final Utf8String TAG_TOT  = new Utf8String("total");

    @Override
    public void printXml(XMLWriter writer,
                         int timePassed,
                         int verbosity)
    {
        Value<N> val = getValues();
        if (!inUse(val) && verbosity < 2) {
            return;
        }
        if (val == null) val = (Value<N>) new LongValue();

        openXMLTag(writer, verbosity);
        writer.attribute(TAG_AVG, new DoubleValue().valueToString(val.getAverage()));
        writer.attribute(TAG_LAST, val.valueToString(val.getLast()));

        if (val.getCount() > 0) {
            writer.attribute(TAG_MIN, val.valueToString(val.getMin()));
            writer.attribute(TAG_MAX, val.valueToString(val.getMax()));
        }
        writer.attribute(TAG_CNT, val.getCount());
        if (verbosity >= 2) {
            writer.attribute(TAG_TOT, val.valueToString(val.getTotal()));
        }

        writer.closeTag();
    }

    public Number getValue(String id) {
        Value<N> val = getValues();
        if (val == null) return 0;

         if (id.equals("last") || (!isAverageMetric() && id.equals("value"))) {
             return val.getLast();
         } else if (id.equals("average") || (isAverageMetric() && id.equals("value"))) {
             return val.getAverage();
         } else if (id.equals("count")) {
             return val.getCount();
         } else if (id.equals("total")) {
             return val.getTotal();
         } else if (id.equals("min")) {
             return val.getMin();
         } else if (id.equals("max")) {
             return val.getMax();
         } else {
             throw new IllegalArgumentException("No id " + id + " in value metric " + getName());
         }
    }

    @Override
    public long getLongValue(String id) {
        return getValue(id).longValue();
    }

    @Override
    public double getDoubleValue(String id) {
        return getValue(id).doubleValue();
    }

    @Override
    public ValueMetric<N> clone(CopyType type, MetricSet owner, boolean includeUnused) {
        return new ValueMetric<>(this, type, owner);
    }

    @Override
    public void reset() { values.reset(); }

    @Override
    public void logEvent(EventLogger logger, String fullName) {
        Value<N> val = getValues();
        if (!doLogIfUnset() && !inUse(val)) {
            return;
        }
        logger.value(fullName, val == null ? 0
                : isAverageMetric() ? val.getAverage()
                                    : val.getLast().doubleValue());
    }

    public boolean inUse(Value<?> value) {
        return (value != null
                && (value.getTotal().longValue() != 0
                    || (value.getCount() != 0 && !isUnsetOnZeroValue())));
    }

    @Override
    public boolean used() {
        return inUse(getValues());
    }

    @Override
    public String toString() {
        Value<N> tmpVals = getValues();
        if (tmpVals == null) {
            tmpVals = (Value<N>) new LongValue();
        }
        return ("count=\"" + tmpVals.getCount() +
                "\" min=\"" + tmpVals.valueToString(tmpVals.getMin()) +
                "\" max=\"" + tmpVals.valueToString(tmpVals.getMax()) +
                "\" last=\""+ tmpVals.valueToString(tmpVals.getLast()) +
                "\" total=\"" + tmpVals.valueToString(tmpVals.getTotal()) +
                "\" average=\"" + new DoubleValue().valueToString(tmpVals.getAverage()) +
                "\"");
    }

}

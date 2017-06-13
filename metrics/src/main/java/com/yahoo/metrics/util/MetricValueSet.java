// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class MetricValueSet
 * \ingroup metrics
 *
 * \brief Utility for doing lockless metric updates and reads.
 *
 * We don't want to use regular locking while updating metrics due to overhead.
 * We use this class to make metric updates as safe as possible without
 * requiring locks.
 *
 * It keeps the set of values a metric wants to set. Thus it is templated on
 * the class keeping the values. All that is required of this class is that it
 * has an empty constructor and a copy constructor.
 *
 * The locking works, by keeping a set of values, with an active pointer into
 * the value vector. Assuming only one thread calls setValues(), it can update
 * the active pointer safely. We assume updating the active pointer is a
 * non-interruptable operations, such that other threads will see either the new
 * or the old value correctly. This should be the case on our platforms.
 *
 * Due to the reset functionality, it is possible to miss out on a metrics
 * added during the reset, but this is very unlikely. For that to happen, when
 * someone sets the reset flag, the writer thread must be in setValues(),
 * having already passed the check for the reset flag, but not finished setting
 * the values yet.
 */

package com.yahoo.metrics.util;

import java.util.Collection;
import com.yahoo.metrics.JoinBehavior;

public class MetricValueSet<Value extends HasCopy<Value> & ValueType>
    implements MetricValueKeeper<Value>
{
    private final ValueType[] values;

    private void setIndexedValue(int idx, Value v) {
        values[idx] = v;
    }

    @SuppressWarnings("unchecked")
    private Value getIndexedValue(int idx) {
        return (Value) values[idx];
    }


    private volatile int activeValueIndex;
    private volatile boolean reset = false;

    public MetricValueSet() {
        values = new ValueType[3];
        activeValueIndex = 0;
    }

    public MetricValueSet(MetricValueSet<Value> other, int copyCount) {
        values = new ValueType[copyCount];
        activeValueIndex = 0;
        setValue(other.getValue());
    }

    /** Get the current values. */
    public Value getValue() {
        if (reset) return null;
        Value v = getIndexedValue(activeValueIndex);
        return (v == null ? null : v.copyObject());
    }

    /**
     * Get the current values from the metric. This function should not be
     * called in parallel. Only call it from a single thread or use external
     * locking. If it returns false, it means the metric have just been reset.
     * In which case, redo getValues(), apply the update again, and call
     * setValues() again.
     */
    public boolean setValue(Value value) {
        int nextIndex = (activeValueIndex + 1) % values.length;
        if (reset) {
            reset = false;
            setIndexedValue(nextIndex, null);
            activeValueIndex = nextIndex;
            return false;
        } else {
            setIndexedValue(nextIndex, value);
            activeValueIndex = nextIndex;
            return true;
        }
    }

    /**
     * Retrieve and reset in a single operation, to minimize chance of
     * alteration in the process.
     */
    public Value getValuesAndReset() {
        Value result = getValue();
        reset = true;
        return result;
    }

    public String toString() {
        String retVal = "MetricValueSet(reset=" + reset
                      + ", active " + activeValueIndex + "[";
        for (ValueType n : values) retVal += n + ",";
        retVal += "])";
        return retVal;
    }

    public int size() { return values.length; }

    public void add(Value v) {
        while (true) {
            Value current = getValue();
            if (current != null) {
                current.add(v);
                if (setValue(current)) {
                    return;
                }
            } else {
                if (setValue(v)) {
                    return;
                }
            }
        }
    }

    public void set(Value v) {
        setValue(v);
    }

    public Value get(JoinBehavior joinBehavior) {
        return getValue();
    }

    public void reset() { reset = true; }

    public Collection<Value> getDirectoryView() {
        return null;
    }

}

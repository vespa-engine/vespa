// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.util;

import com.yahoo.concurrent.ThreadLocalDirectory;
import com.yahoo.metrics.DoubleValue;
import com.yahoo.metrics.LongValue;
import com.yahoo.metrics.JoinBehavior;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Value keeper class using ThreadLocalDirectory to maintain thread safety from multiple threads writing.
 */
public class ThreadLocalDirectoryValueKeeper<Value extends ValueType & HasCopy<Value>>
    implements MetricValueKeeper<Value>, ThreadLocalDirectory.ObservableUpdater<Value, Value>
{
    private final ThreadLocalDirectory<Value, Value> directory = new ThreadLocalDirectory<Value, Value>(this);
    private List<Value> initialValues = null;

    public ThreadLocalDirectoryValueKeeper() {
    }

    public ThreadLocalDirectoryValueKeeper(MetricValueKeeper<Value> other) {
        initialValues = new LinkedList<Value>();
        for (Value v : other.getDirectoryView()) initialValues.add(v.copyObject());
    }

    public Value createGenerationInstance(Value value) {
        return null;
    }

    public Value update(Value current, Value newValue) {
        if (current == null) {
            if (newValue instanceof DoubleValue) {
                current = (Value) new DoubleValue();
            } else {
                current = (Value) new LongValue();
            }
        }
        current.add(newValue);
        return current;
    }

    public Value copy(Value value) {
        return value.copyObject();
    }

    public void add(Value v) {
        directory.update(v, directory.getLocalInstance());
    }

    public void set(Value v) {
        throw new UnsupportedOperationException("This value keeper is only intended to use with active metrics. Set operation not supported.");
    }

    public Value get(JoinBehavior joinBehavior) {
        List<Value> values = directory.view();
        if (initialValues != null) values.addAll(initialValues);
        if (values.isEmpty()) {
            return null;
        }
        return (Value) values.get(0).join(Collections.<ValueType>unmodifiableList(values), joinBehavior);
    }

    public void reset() {
        directory.fetch();
        initialValues = null;
    }

    public Collection<Value> getDirectoryView() {
        return directory.view();
    }
}

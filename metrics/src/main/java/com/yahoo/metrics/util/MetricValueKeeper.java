// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.util;

import com.yahoo.metrics.JoinBehavior;
import java.util.Collection;

/**
 *
 */
public interface MetricValueKeeper<Value> {
    /** Add a value to a given value. Used when metrics are written to by metric writer. */
    public void add(Value v);
    /** Set the value to exactly this, forgetting old value. This operation needs only be supported by value keepers used in inactive snapshots. */
    public void set(Value v);
    /** Get the value of the metrics written. If multiple values exist, use given join behavior. */
    public Value get(JoinBehavior joinBehavior);
    /** Reset the value of the metrics to zero. */
    public void reset();
    /** Return copy of current view */
    public Collection<Value> getDirectoryView();
}

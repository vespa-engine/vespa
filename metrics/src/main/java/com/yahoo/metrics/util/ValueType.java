// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.util;

import com.yahoo.metrics.JoinBehavior;

import java.util.Collection;

public interface ValueType extends Cloneable {
    public void add(ValueType other);
    public ValueType join(Collection<ValueType> sources, JoinBehavior joinBehavior);
}

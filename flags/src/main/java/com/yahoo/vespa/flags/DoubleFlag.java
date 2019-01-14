// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import javax.annotation.concurrent.Immutable;

/**
 * @author freva
 */
@Immutable
public class DoubleFlag extends FlagImpl<Double, DoubleFlag> {
    public DoubleFlag(FlagId id, Double defaultValue, FetchVector vector, FlagSerializer<Double> serializer, FlagSource source) {
        super(id, defaultValue, vector, serializer, source, DoubleFlag::new);
    }

    public double value() {
        return boxedValue();
    }
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * @author freva
 */
public class DoubleFlag extends FlagImpl<Double, DoubleFlag> {
    public DoubleFlag(FlagId id, Double defaultValue, FetchVector vector, FlagSerializer<Double> serializer, FlagSource source) {
        super(id, defaultValue, vector, serializer, source, DoubleFlag::new);
    }

    @Override
    public DoubleFlag self() {
        return this;
    }

    public double value() {
        return boxedValue();
    }
}

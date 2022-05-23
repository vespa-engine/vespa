// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;


/**
 * @author hakonhall
 */
public class IntFlag extends FlagImpl<Integer, IntFlag> {
    public IntFlag(FlagId id, int defaultValue, FetchVector vector, FlagSerializer<Integer> serializer, FlagSource source) {
        super(id, defaultValue, vector, serializer, source, IntFlag::new);
    }

    @Override
    public IntFlag self() {
        return this;
    }

    public int value() {
        return boxedValue();
    }
}

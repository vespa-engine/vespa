// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * @author hakonhall
 */
public class BooleanFlag extends FlagImpl<Boolean, BooleanFlag> {
    public BooleanFlag(FlagId id, boolean defaultValue, FetchVector vector, FlagSerializer<Boolean> serializer, FlagSource source) {
        super(id, defaultValue, vector, serializer, source, BooleanFlag::new);
    }

    @Override
    public BooleanFlag self() {
        return this;
    }

    public boolean value() {
        return boxedValue();
    }
}

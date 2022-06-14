// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * @author hakonhall
 */
public class LongFlag extends FlagImpl<Long, LongFlag> {
    public LongFlag(FlagId id, Long defaultValue, FetchVector vector, FlagSerializer<Long> serializer, FlagSource source) {
        super(id, defaultValue, vector, serializer, source, LongFlag::new);
    }

    @Override
    public LongFlag self() {
        return this;
    }

    public long value() {
        return boxedValue();
    }
}

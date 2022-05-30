// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * @author hakonhall
 */
public class JacksonFlag<T> extends FlagImpl<T, JacksonFlag<T>> {
    public JacksonFlag(FlagId id, T defaultValue, FetchVector vector, FlagSerializer<T> serializer, FlagSource source) {
        super(id, defaultValue, vector, serializer, source, JacksonFlag::new);
    }

    @Override
    public JacksonFlag<T> self() {
        return this;
    }

    public T value() {
        return boxedValue();
    }
}

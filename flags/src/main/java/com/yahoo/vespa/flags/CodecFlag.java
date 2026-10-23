// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * @author Håkon Hallingstad
 */
public class CodecFlag<T> extends FlagImpl<T, CodecFlag<T>> {
    public CodecFlag(FlagId id, T defaultValue, FetchVector fetchVector, FlagSerializer<T> serializer, FlagSource source) {
        super(id, defaultValue, fetchVector, serializer, source, CodecFlag::new);
    }

    @Override public CodecFlag<T> self() { return this; }
    public T value() { return boxedValue(); }
}

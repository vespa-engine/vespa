// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import javax.annotation.concurrent.Immutable;

/**
 * @author hakonhall
 */
@Immutable
public class Flag<T> {
    private final FlagId id;
    private final T defaultValue;
    private final FlagSource source;
    private final Deserializer<T> deserializer;
    private final FetchVector fetchVector;

    public Flag(String flagId, T defaultValue, FlagSource source, Deserializer<T> deserializer) {
        this(new FlagId(flagId), defaultValue, source, deserializer);
    }

    public Flag(FlagId id, T defaultValue, FlagSource source, Deserializer<T> deserializer) {
        this(id, defaultValue, deserializer, new FetchVector(), source);
    }

    public Flag(FlagId id, T defaultValue, Deserializer<T> deserializer, FetchVector fetchVector, FlagSource source) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.source = source;
        this.deserializer = deserializer;
        this.fetchVector = fetchVector;
    }

    public FlagId id() {
        return id;
    }

    public Flag<T> with(FetchVector.Dimension dimension, String value) {
        return new Flag<>(id, defaultValue, deserializer, fetchVector.with(dimension, value), source);
    }

    public T value() {
        return source.fetch(id, fetchVector).map(deserializer::deserialize).orElse(defaultValue);
    }
}

// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import javax.annotation.concurrent.Immutable;

/**
 * @author hakonhall
 */
@Immutable
public class UnboundFlag<T> {
    private final FlagId id;
    private final T defaultValue;
    private final Deserializer<T> deserializer;
    private final FetchVector fetchVector;

    public UnboundFlag(String flagId, T defaultValue, Deserializer<T> deserializer) {
        this(new FlagId(flagId), defaultValue, deserializer, new FetchVector());
    }

    public UnboundFlag(FlagId id, T defaultValue, Deserializer<T> deserializer, FetchVector fetchVector) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.deserializer = deserializer;
        this.fetchVector = fetchVector;
    }

    public FlagId id() {
        return id;
    }

    public UnboundFlag<T> with(FetchVector.Dimension dimension, String value) {
        return new UnboundFlag<>(id, defaultValue, deserializer, fetchVector.with(dimension, value));
    }

    public Flag<T> bindTo(FlagSource source) {
        return new Flag<>(id, defaultValue, deserializer, fetchVector, source);
    }
}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * @author hakonhall
 */
public abstract class FlagImpl<T, F extends FlagImpl<T, F>> implements Flag<T, F> {
    private final FlagId id;
    private final T defaultValue;
    private final FlagSerializer<T> serializer;
    private final FetchVector fetchVector;
    private final UnboundFlagImpl.FlagFactory<T, F> factory;
    private final FlagSource source;

    protected FlagImpl(FlagId id,
                       T defaultValue,
                       FetchVector fetchVector, FlagSerializer<T> serializer,
                       FlagSource source,
                       UnboundFlagImpl.FlagFactory<T, F> factory) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.serializer = serializer;
        this.fetchVector = fetchVector;
        this.factory = factory;
        this.source = source;
    }

    @Override
    public FlagId id() {
        return id;
    }

    @Override
    public F with(Dimension dimension, String dimensionValue) {
        return factory.create(id, defaultValue, fetchVector.with(dimension, dimensionValue), serializer, source);
    }

    @Override
    public T boxedValue() {
        return source.fetch(id, fetchVector).map(serializer::deserialize).orElse(defaultValue);
    }

    @Override
    public FlagSerializer<T> serializer() {
        return serializer;
    }
}

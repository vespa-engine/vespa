// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * @author hakonhall
 */
public abstract class UnboundFlagImpl<T, F extends Flag<T, F>, U extends UnboundFlag<T, F, U>>
        implements UnboundFlag<T, F, U> {

    private final FlagId id;
    private final T defaultValue;
    private final FlagSerializer<T> serializer;
    private final FetchVector defaultFetchVector;
    private final UnboundFlagFactory<T, F, U> unboundFlagFactory;
    private final FlagFactory<T, F> flagFactory;

    public interface UnboundFlagFactory<T1, F1 extends Flag<T1, F1>, U1 extends UnboundFlag<T1, F1, U1>> {
        U1 create(FlagId id, T1 defaultValue, FetchVector fetchVector);
    }

    public interface FlagFactory<T2, F2 extends Flag<T2, F2>> {
        F2 create(FlagId id, T2 defaultValue, FetchVector fetchVector, FlagSerializer<T2> serializer, FlagSource source);
    }

    protected UnboundFlagImpl(FlagId id,
                              T defaultValue,
                              FetchVector defaultFetchVector,
                              FlagSerializer<T> serializer,
                              UnboundFlagFactory<T, F, U> unboundFlagFactory,
                              FlagFactory<T, F> flagFactory) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.serializer = serializer;
        this.defaultFetchVector = defaultFetchVector;
        this.unboundFlagFactory = unboundFlagFactory;
        this.flagFactory = flagFactory;
    }

    @Override
    public FlagId id() {
        return id;
    }

    @Override
    public U with(Dimension dimension, String dimensionValue) {
        return unboundFlagFactory.create(id, defaultValue, defaultFetchVector.with(dimension, dimensionValue));
    }

    @Override
    public F bindTo(FlagSource source) {
        return flagFactory.create(id, defaultValue, defaultFetchVector, serializer, source);
    }

    @Override
    public FlagSerializer<T> serializer() {
        return serializer;
    }

    @Override
    public T defaultValue() {
        return defaultValue;
    }
}

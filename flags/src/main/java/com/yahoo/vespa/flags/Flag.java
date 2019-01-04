// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * Interface for flag.
 *
 * @param <T> The type of the flag value (boxed for primitives)
 * @param <F> The concrete subclass type of the flag
 * @author hakonhall
 */
public interface Flag<T, F> {
    /** The flag ID. */
    FlagId id();

    /** Returns the flag serializer. */
    FlagSerializer<T> serializer();

    /** Returns an immutable clone of the current object, except with the dimension set accordingly. */
    F with(FetchVector.Dimension dimension, String dimensionValue);

    /** Returns the value, boxed if the flag wraps a primitive type. */
    T boxedValue();
}

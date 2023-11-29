// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.Optional;

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

    /** A generic type-safe method for getting {@code this}. */
    F self();

    /** Returns the flag serializer. */
    FlagSerializer<T> serializer();

    /** Returns an immutable clone of the current object, except with the dimension set accordingly. */
    F with(Dimension dimension, String dimensionValue);

    /** Same as {@link #with(Dimension, String)} if value is present, and otherwise returns {@code this}. */
    default F with(Dimension dimension, Optional<String> dimensionValue) {
        return dimensionValue.map(value -> with(dimension, value)).orElse(self());
    }

    /** Returns the value, boxed if the flag wraps a primitive type. */
    T boxedValue();
}

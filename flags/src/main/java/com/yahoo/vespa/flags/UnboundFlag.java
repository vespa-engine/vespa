// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * Interface of an unbound flag.
 *
 * @param <T> Type of boxed value, e.g. Integer
 * @param <F> Type of flag, e.g. IntFlag
 * @param <U> Type of unbound flag, e.g. UnboundIntFlag
 *
 * @author hakonhall
 */
public interface UnboundFlag<T, F extends Flag<T, F>, U extends UnboundFlag<T, F, U>> {
    /** The flag ID. */
    FlagId id();

    /** Returns the flag serializer. */
    FlagSerializer<T> serializer();

    /** Returns a clone of the unbound flag, but with the dimension set accordingly. */
    U with(Dimension dimension, String dimensionValue);

    /** Binds to a flag source, returning a (bound) flag. */
    F bindTo(FlagSource source);

    /** Returns the default value of the flag */
    T defaultValue();
}

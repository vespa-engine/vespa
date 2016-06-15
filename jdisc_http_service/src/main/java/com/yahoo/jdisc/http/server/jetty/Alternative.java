// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Simple monad class, like Optional but with support for chaining alternatives in preferred order.
 *
 * Holds a current value (immutably), but if the current value is null provides an easy way to obtain an instance
 * with another value, ad infinitum.
 *
 * Instances of this class are immutable and thread-safe.
 *
 * @author bakksjo
 */
public class Alternative<T> {
    private final T value;

    private Alternative(final T value) {
        this.value = value;
    }

    /**
     * Creates an instance with the supplied value.
     */
    public static <T> Alternative<T> preferred(final T value) {
        return new Alternative<>(value);
    }

    /**
     * Returns itself (unchanged) iff current value != null,
     * otherwise returns a new instance with the value supplied by the supplier.
     */
    public Alternative<T> alternatively(final Supplier<? extends T> supplier) {
        if (value != null) {
            return this;
        }

        return new Alternative<>(supplier.get());
    }

    /**
     * Returns the held value iff != null, otherwise invokes the supplier and returns its value.
     */
    public T orElseGet(final Supplier<? extends T> supplier) {
        if (value != null) {
            return value;
        }
        return supplier.get();
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof Alternative<?>)) {
            return false;
        }

        final Alternative<?> other = (Alternative<?>) o;

        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}

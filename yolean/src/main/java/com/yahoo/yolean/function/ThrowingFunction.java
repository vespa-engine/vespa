// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.function;

import java.util.Objects;

/**
 * Functional interface that mirrors the Function interface, but allows for an
 * exception to be thrown.
 *
 * @author oyving
 */
@FunctionalInterface
public interface ThrowingFunction<T, R, E extends Throwable> {
    R apply(T input) throws E;

    default <V> ThrowingFunction<T, V, E> andThen(ThrowingFunction<? super R, ? extends V, ? extends E> after) {
        Objects.requireNonNull(after);
        return (T t) -> after.apply(apply(t));
    }

    default <V> ThrowingFunction<V, R, E> compose(ThrowingFunction<? super V, ? extends T, ? extends E> before) {
        Objects.requireNonNull(before);
        return (V v) -> apply(before.apply(v));
    }
}

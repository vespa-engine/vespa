// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.stream;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * The purpose of this class is to fill gaps in the Java {@link Collectors} api
 * by offering convenient ways to retrieve implementations of {@link Collector}.
 *
 * <p>For example, to get a collector that accumulates elements into a map with
 * predictable iteration order:
 * <pre>{@code
 *
 *     Map<String, Person> idToPerson =
 *         persons.stream().collect(toLinkedMap(Person::id, Functions.identity());
 * }</pre>
 *
 * @author gjoranv
 */
public class CustomCollectors {

    private CustomCollectors() { }

    /**
     * Returns a {@code Collector} that accumulates elements into a {@code Map}
     * that provides insertion order iteration. This a convenience that can be used
     * instead of calling {@link java.util.stream.Collectors#toMap(Function, Function, BinaryOperator, Supplier)}.
     * with a merger that throws upon duplicate keys.
     *
     * @param keyMapper Mapping function to produce keys.
     * @param valueMapper Mapping function to produce values.
     * @param <T> Type of the input elements.
     * @param <K> Output type of the key mapping function.
     * @param <U> Output type of the value mapping function.
     * @return A collector which collects elements into a map with insertion order iteration.
     * @throws DuplicateKeyException If two elements map to the same key.
     */
    public static <T, K, U>
    Collector<T, ?, Map<K,U>> toLinkedMap(Function<? super T, ? extends K> keyMapper,
                                          Function<? super T, ? extends U> valueMapper) {
        return Collectors.toMap(keyMapper, valueMapper, throwingMerger(), LinkedHashMap::new);
    }

    /**
     * Returns a {@code Collector} that accumulates elements into a {@code Map}
     * created by the given supplier. This a convenience that can be used
     * instead of calling {@link java.util.stream.Collectors#toMap(Function, Function, BinaryOperator, Supplier)}.
     * with a merger that throws upon duplicate keys.
     *
     * @param keyMapper Mapping function to produce keys.
     * @param valueMapper Mapping function to produce values.
     * @param mapSupplier Supplier of a new map.
     * @param <T> Type of the input elements.
     * @param <K> Output type of the key mapping function.
     * @param <U> Output type of the value mapping function.
     * @param <M> Type of the resulting map.
     * @return A collector which collects elements into a map created by the given supplier.
     * @throws DuplicateKeyException If two elements map to the same key.
     */
    public static <T, K, U, M extends Map<K,U>>
    Collector<T, ?, M> toCustomMap(Function<? super T, ? extends K> keyMapper,
                                   Function<? super T, ? extends U> valueMapper,
                                   Supplier<M> mapSupplier) {
        return Collectors.toMap(keyMapper, valueMapper, throwingMerger(), mapSupplier);
    }


    private static <T> BinaryOperator<T> throwingMerger() {
        return (u,v) -> { throw new DuplicateKeyException(u); };
    }

    public static class DuplicateKeyException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        DuplicateKeyException(Object key) {
            super(String.format("Duplicate keys: %s", key));
        }
    }
}

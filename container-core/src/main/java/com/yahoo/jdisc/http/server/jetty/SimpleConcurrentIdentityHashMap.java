// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A simplified {@link ConcurrentMap} using reference-equality to compare keys (similarly to {@link java.util.IdentityHashMap})
 *
 * @author bjorncs
 */
class SimpleConcurrentIdentityHashMap<K, V> {

    private final ConcurrentMap<IdentityKey<K>, V> wrappedMap = new ConcurrentHashMap<>();

    Optional<V> get(K key) { return Optional.ofNullable(wrappedMap.get(identityKey(key))); }

    Optional<V> remove(K key) { return Optional.ofNullable(wrappedMap.remove(identityKey(key))); }

    Optional<V> put(K key, V value) { return Optional.ofNullable(wrappedMap.put(identityKey(key), value)); }

    V computeIfAbsent(K key, Supplier<V> supplier) {
        return wrappedMap.computeIfAbsent(identityKey(key), ignored -> supplier.get());
    }

    V computeIfAbsent(K key, Function<K, V> factory) {
        return wrappedMap.computeIfAbsent(identityKey(key), k -> factory.apply(k.instance));
    }

    private static <K> IdentityKey<K> identityKey(K key) { return IdentityKey.of(key); }

    private static class IdentityKey<K> {
        final K instance;

        IdentityKey(K instance) { this.instance = instance; }

        static <K> IdentityKey<K> of(K instance) { return new IdentityKey<>(instance); }

        @Override public int hashCode() { return System.identityHashCode(instance); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof IdentityKey<?>)) return false;
            IdentityKey<?> other = (IdentityKey<?>) obj;
            return this.instance == other.instance;
        }
    }
}

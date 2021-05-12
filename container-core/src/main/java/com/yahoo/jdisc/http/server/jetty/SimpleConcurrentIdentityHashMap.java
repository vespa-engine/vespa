// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A simplified {@link ConcurrentMap} using reference-equality to compare keys (similarly to {@link java.util.IdentityHashMap})
 *
 * @author bjorncs
 */
class SimpleConcurrentIdentityHashMap<K, V> {

    private final ConcurrentMap<IdentityKey<K>, V> wrappedMap = new ConcurrentHashMap<>();

    V get(K key) { return wrappedMap.get(IdentityKey.of(key)); }

    V remove(K key) { return wrappedMap.remove(IdentityKey.of(key)); }

    void put(K key, V value) { wrappedMap.put(IdentityKey.of(key), value); }

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

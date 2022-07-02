// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A read-only map which forwards lookups to a primary map, and then a secondary for
 * keys not existing in the primary.
 *
 * @author bratseth
 */
class ChainedMap<K, V> implements Map<K, V> {

    private final Map<K, V> primary, secondary;

    ChainedMap(Map<K, V> primary, Map<K, V> secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public int size() {
        return (primary.size() >= secondary.size())
                ? countUnique(primary, secondary)
                : countUnique(secondary, primary);
    }

    private int countUnique(Map<K, V> large, Map<K,V> small) {
        int size = large.size();
        for (K key : small.keySet()) {
            if ( ! large.containsKey(key)) size++;
        }
        return size;
    }

    @Override
    public boolean isEmpty() {
        return primary.isEmpty() && secondary.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return primary.containsKey(key) || secondary.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return primary.containsValue(value) || secondary.containsValue(value);
    }

    @Override
    public V get(Object key) {
        V value = primary.get(key);
        return value != null ? value : secondary.get(key);
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<K> keySet() {
        var keys = new HashSet<>(secondary.keySet());
        keys.addAll(primary.keySet());
        return keys;
    }

    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

}

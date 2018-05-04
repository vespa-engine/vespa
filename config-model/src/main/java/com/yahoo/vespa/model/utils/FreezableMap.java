// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.utils;

import java.util.*;

/**
 * Delegates to a map that can be frozen.
 * Not thread safe.
 *
 * @author tonytv
 */
public class FreezableMap<K, V> implements Map<K, V> {
    private boolean frozen = false;
    private Map<K, V> map;

    //TODO: review the use of unchecked.
    @SuppressWarnings("unchecked")
    public FreezableMap(Class<LinkedHashMap> mapClass) {
        try {
            map = mapClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean containsKey(Object o) {
        return map.containsKey(o);
    }

    public boolean containsValue(Object o) {
        return map.containsValue(o);
    }

    public V get(Object o) {
        return map.get(o);
    }

    public V put(K key, V value) {
        return map.put(key, value);
    }

    public V remove(Object o) {
        return map.remove(o);
    }

    public void putAll(Map<? extends K, ? extends V> map) {
        this.map.putAll(map);
    }

    public void clear() {
        map.clear();
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public Collection<V> values() {
        return map.values();
    }

    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    public boolean equals(Object o) {
        return map.equals(o);
    }

    public int hashCode() {
        return map.hashCode();
    }

    public void freeze() {
        if (frozen)
            throw new RuntimeException("The map has already been frozen.");
        frozen = true;
        map = Collections.unmodifiableMap(map);
    }

    public boolean isFrozen() {
        return frozen;
    }

}

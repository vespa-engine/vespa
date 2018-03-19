// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.concurrent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * <p>This is a thread hash map for small collections that are stable once built. Until it is stable there will be a
 * race among all threads missing something in the map. They will then clone the map add the missing stuff and then put
 * it back as active again. Here are no locks, but the cost is that inserts will happen a lot more than necessary. The
 * map reference is volatile, but on most multi-cpu machines that has no cost unless modified.</p>
 *
 * @author baldersheim
 */
public class CopyOnWriteHashMap<K, V> implements Map<K, V> {

    private volatile HashMap<K, V> map = new HashMap<>();

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return map.get(key);
    }

    @Override
    public V put(K key, V value) {
        HashMap<K, V> next = new HashMap<>(map);
        V old = next.put(key, value);
        map = next;
        return old;
    }

    @Override
    @SuppressWarnings("SuspiciousMethodCalls")
    public V remove(Object key) {
        HashMap<K, V> prev = map;
        if (!prev.containsKey(key)) {
            return null;
        }
        HashMap<K, V> next = new HashMap<>(prev);
        V old = next.remove(key);
        map = next;
        return old;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        HashMap<K, V> next = new HashMap<>(map);
        next.putAll(m);
        map = next;
    }

    @Override
    public void clear() {
        map = new HashMap<>();
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A hashmap wrapper which defers cloning of the enclosed map until it is written to.
 * Use this to make clones cheap in maps which are often not further modified.
 * <p>
 * As with regular maps, this can only be used safely if the content of the map is immutable.
 * If not, the {@link #copyMap} method can be overridden to perform a deep clone.
 *
 * @author bratseth
 */
public class CopyOnWriteHashMap<K,V> extends AbstractMap<K,V> implements Cloneable {

    private Map<K,V> map;

    /** This class may write to the map if it is the sole owner */
    private AtomicInteger owners = new AtomicInteger(1);

    /** Lazily initialized view */
    private transient Set<Map.Entry<K,V>> entrySet = null;

    public CopyOnWriteHashMap() {
        this.map = new HashMap<>();
    }

    public CopyOnWriteHashMap(int capacity) {
        this.map = new HashMap<>(capacity);
    }

    public CopyOnWriteHashMap(Map<K,V> map) {
        this.map = new HashMap<>(map);
    }

    private void makeReadOnly() {
        owners.incrementAndGet();
    }

    private boolean isWritable() {
        return owners.get() == 1;
    }

    private void makeWritable() {
        if (isWritable()) return;
        map = copyMap(map);
        owners.decrementAndGet();
        owners = new AtomicInteger(1);
        entrySet = null;
    }

    /**
     * Make a copy of the given map with the requisite deepness.
     * This default implementation does return new HashMap&lt;&gt;(original);
     */
    protected Map<K,V> copyMap(Map<K,V> original) {
        return new HashMap<>(original);
    }

    @SuppressWarnings("unchecked")
    public CopyOnWriteHashMap<K,V> clone() {
        try {
            CopyOnWriteHashMap<K,V> clone = (CopyOnWriteHashMap<K,V>)super.clone();
            makeReadOnly(); // owners shared with clone
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        if (entrySet == null)
            entrySet = new EntrySet();
        return entrySet;
    }

    @Override
    public V put(K key, V value) {
        makeWritable();
        return map.put(key, value);
    }

    /** Override to avoid using iterator.remove */
    @Override
    public V remove(Object key) {
        makeWritable();
        return map.remove(key);
    }

    @Override
    public boolean equals(Object other) {
        if ( ! (other instanceof CopyOnWriteHashMap)) return false;
        return this.map.equals(((CopyOnWriteHashMap<?, ?>)other).map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    private final class EntrySet extends AbstractSet<Map.Entry<K,V>> {

        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }

        @SuppressWarnings("unchecked")
        public boolean contains(Object o) {
            if ( ! (o instanceof Map.Entry)) return false;
            Map.Entry<K,V> entry = (Map.Entry<K,V>) o;
            Object candidate = map.get(entry.getKey());
            if (candidate == null) return entry.getValue()==null;
            return candidate.equals(entry.getValue());
        }

        public boolean remove(Object o) {
            makeWritable();
            return map.remove(o) !=null;
        }

        public int size() {
            return map.size();
        }

        public void clear() { map.clear(); }

    }

    /**
     * An entry iterator which does not allow removals if the map wasn't already modifiable
     * There is no sane way to implement that given that the wrapped map changes mid iteration.
     */
    private class EntryIterator implements Iterator<Map.Entry<K,V>> {

        /** Wrapped iterator */
        private final Iterator<Map.Entry<K,V>> mapIterator;

        public EntryIterator() {
            mapIterator = map.entrySet().iterator();
        }

        public final boolean hasNext() {
            return mapIterator.hasNext();
        }

        public Entry<K,V> next() {
            return mapIterator.next();
        }

        public void remove() {
            if ( ! isWritable())
                throw new UnsupportedOperationException("Cannot perform the copy-on-write operation during iteration");
            mapIterator.remove();
        }

    }

}

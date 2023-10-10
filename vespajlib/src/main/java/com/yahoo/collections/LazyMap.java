// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * @author Simon Thoresen Hult
 */
public abstract class LazyMap<K, V> implements Map<K, V> {

    private Map<K, V> delegate = newEmpty();

    @Override
    public final int size() {
        return delegate.size();
    }

    @Override
    public final boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public final boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public final boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public final V get(Object key) {
        return delegate.get(key);
    }

    @Override
    public final V put(K key, V value) {
        return delegate.put(key, value);
    }

    @Override
    public final V remove(Object key) {
        return delegate.remove(key);
    }

    @Override
    public final void putAll(Map<? extends K, ? extends V> m) {
        delegate.putAll(m);
    }

    @Override
    public final void clear() {
        delegate.clear();
    }

    @Override
    public final Set<K> keySet() {
        return delegate.keySet();
    }

    @Override
    public final Collection<V> values() {
        return delegate.values();
    }

    @Override
    public final Set<Entry<K, V>> entrySet() {
        return delegate.entrySet();
    }

    @Override
    public final int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
        return obj == this || (obj instanceof Map && delegate.equals(obj));
    }

    private Map<K, V> newEmpty() {
        return new EmptyMap();
    }

    private Map<K, V> newSingleton(K key, V value) {
        return new SingletonMap(key, value);
    }

    protected abstract Map<K, V> newDelegate();

    final Map<K, V> getDelegate() {
        return delegate;
    }

    class EmptyMap extends AbstractMap<K, V> {

        @Override
        public V put(K key, V value) {
            delegate = newSingleton(key, value);
            return null;
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> m) {
            switch (m.size()) {
            case 0:
                break;
            case 1:
                Entry<? extends K, ? extends V> entry = m.entrySet().iterator().next();
                put(entry.getKey(), entry.getValue());
                break;
            default:
                delegate = newDelegate();
                delegate.putAll(m);
                break;
            }
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return Collections.emptySet();
        }
    }

    class SingletonMap extends AbstractMap<K, V> {

        final K key;
        V value;

        SingletonMap(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public V put(K key, V value) {
            if (containsKey(key)) {
                V oldValue = this.value;
                this.value = value;
                return oldValue;
            } else {
                delegate = newDelegate();
                delegate.put(this.key, this.value);
                return delegate.put(key, value);
            }
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> m) {
            switch (m.size()) {
            case 0:
                break;
            case 1:
                Entry<? extends K, ? extends V> entry = m.entrySet().iterator().next();
                put(entry.getKey(), entry.getValue());
                break;
            default:
                delegate = newDelegate();
                delegate.put(this.key, this.value);
                delegate.putAll(m);
                break;
            }
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return new AbstractSet<Entry<K, V>>() {

                @Override
                public Iterator<Entry<K, V>> iterator() {
                    return new Iterator<Entry<K, V>>() {

                        boolean hasNext = true;

                        @Override
                        public boolean hasNext() {
                            return hasNext;
                        }

                        @Override
                        public Entry<K, V> next() {
                            if (hasNext) {
                                hasNext = false;
                                return new Entry<K, V>() {

                                    @Override
                                    public K getKey() {
                                        return key;
                                    }

                                    @Override
                                    public V getValue() {
                                        return value;
                                    }

                                    @Override
                                    public V setValue(V value) {
                                        V oldValue = SingletonMap.this.value;
                                        SingletonMap.this.value = value;
                                        return oldValue;
                                    }

                                    @Override
                                    public int hashCode() {
                                        return Objects.hashCode(key) + Objects.hashCode(value) * 31;
                                    }

                                    @Override
                                    public boolean equals(Object obj) {
                                        if (obj == this) {
                                            return true;
                                        }
                                        if (!(obj instanceof Entry)) {
                                            return false;
                                        }
                                        @SuppressWarnings("unchecked")
                                        Entry<K, V> rhs = (Entry<K, V>)obj;
                                        if (!Objects.equals(key, rhs.getKey())) {
                                            return false;
                                        }
                                        if (!Objects.equals(value, rhs.getValue())) {
                                            return false;
                                        }
                                        return true;
                                    }
                                };
                            } else {
                                throw new NoSuchElementException();
                            }
                        }

                        @Override
                        public void remove() {
                            if (hasNext) {
                                throw new IllegalStateException();
                            } else {
                                delegate = newEmpty();
                            }
                        }
                    };
                }

                @Override
                public int size() {
                    return 1;
                }
            };
        }
    }

    public static <K, V> LazyMap<K, V> newHashMap() {
        return new LazyMap<K, V>() {

            @Override
            protected Map<K, V> newDelegate() {
                return new HashMap<>();
            }
        };
    }
}

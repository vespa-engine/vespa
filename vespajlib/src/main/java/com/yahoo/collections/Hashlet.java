// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;


/**
 * Lightweight hash map from key to value with limited
 * functionality. This class lets you build a map from key to
 * value. The value for a key may be overwritten and the put and get
 * methods have the same semantics as for normal Java Maps, but there
 * is no remove operation. Also, there is no iterator support, but
 * keys and values can be accessed directly by index. The access order
 * of keys and values are defined by the insert order of the keys. The
 * goal of this class is to reduce the amount of object that are
 * allocated by packing everything into two internal arrays. The keys
 * and values are packed in an Object array and the hash table and
 * entries are packed in an int array. The internal arrays are not
 * created until space is needed. The default initial capacity is 16
 * entries. If you know you need much more space than this, you can
 * explicitly reserve more space before starting to insert values. The
 * maximum load factor is 0.7 and drops slightly with increasing
 * capacity.
 *
 * @author Havard Pettersen
 */
public final class Hashlet<K, V> {

    private static final int[] emptyHash = new int[1];
    private int capacity = 0;
    private int hashSize() { return (capacity + (capacity / 2) - 1); }
    private int used = 0;
    private Object[] store;
    private int[] hash = emptyHash;

    /**
     * Create an empty Hashlet.
     **/
    public Hashlet() {}

    /**
     * Create a Hashlet that is a shallow copy of another Hashlet.
     *
     * @param hashlet the Hashlet to copy.
     **/
    public Hashlet(Hashlet<K, V> hashlet) {
        if (hashlet.used > 0) {
            capacity = hashlet.capacity;
            used = hashlet.used;
            store = new Object[hashlet.store.length];
            hash = new int[hashlet.hash.length];
            System.arraycopy(hashlet.store, 0, store, 0, store.length);
            System.arraycopy(hashlet.hash, 0, hash, 0, hash.length);
        }
    }

    /**
     * Reserve space for more key value pairs. This method is used by
     * the put method to perform rehashing when needed. It can be
     * invoked directly by the application to reduce the number of
     * rehashes needed to insert a large number of entries.
     *
     * @param n the number of additional entries to reserve space for
     **/
    public void reserve(int n) {
        if (used + n > capacity) {
            final int c = capacity;
            if (capacity == 0) {
                capacity = 16;
            }
            while (used + n > capacity) {
                capacity *= 2;
            }
            final Object[] s = store;
            store = new Object[capacity * 2];
            hash = new int[hashSize() + (capacity * 2)];
            if (c > 0) {
                System.arraycopy(s, 0, store, 0, used);
                System.arraycopy(s, c, store, capacity, used);
                for (int i = 0; i < used; i++) {
                    int prev = Math.abs(s[i].hashCode() % hashSize());
                    int entry = hash[prev];
                    while (entry != 0) {
                        prev = entry + 1;
                        entry = hash[prev];
                    }
                    final int insertIdx = (hashSize() + (i * 2));
                    hash[prev] = insertIdx;
                    hash[insertIdx] = i;
                }
            }
        }
    }

    /**
     * The current size. This is the number of key value pairs
     * currently stored in this object.
     *
     * @return current size
     **/
    public int size() {
        return used;
    }

    /**
     * Obtain a key. Keys are accessed in the order they were first
     * inserted.
     *
     * @return the requested key
     * @param i the index of the key, must be in the range [0, size() - 1]
     **/
    @SuppressWarnings("unchecked")
    public K key(int i) {
        return (K) store[i];
    }

    /**
     * Obtain a value. Values are accessed in the order in which
     * theirs keys were first inserted.
     *
     * @return the requested value
     * @param i the index of the value, must be in the range [0, size() - 1]
     **/
    @SuppressWarnings("unchecked")
    public V value(int i) {
        return (V) store[capacity + i];
    }

    /**
     * This will replace the value at the index give.
     *
     * @param i the index of the value, must be in the range [0, size() - 1]
     * @param value The new value you want to set for this index.
     * @return previous value
     */
    public V setValue(int i, V value) {
        V prev = value(i);
        store[capacity + i] = value;
        return prev;
    }

    /**
     * Associate a value with a specific key.
     *
     * @return the old value for the key, if it was already present
     * @param key the key
     * @param value the value
     **/
    public V put(K key, V value) {
        reserve(1);
        int prev = Math.abs(key.hashCode() % hashSize());
        int entry = hash[prev];
        while (entry != 0) {
            final int idx = hash[entry];
            if (store[idx].equals(key)) { // found entry
                @SuppressWarnings("unchecked")
                final V ret = (V) store[capacity + idx];
                store[capacity + idx] = value;
                return ret;
            }
            prev = entry + 1;
            entry = hash[prev];
        }
        final int insertIdx = (hashSize() + (used * 2));
        hash[prev] = insertIdx;
        hash[insertIdx] = used;
        store[used] = key;
        store[capacity + (used++)] = value;
        return null;
    }

    /**
     * Obtain the value for a specific key.
     *
     * @return the value for a key, or null if not found
     * @param key the key
     **/
    public V get(Object key) {
        int index = getIndexOfKey(key);
        return (index != -1) ? value(index) : null;
    }

    /**
     * Finds the index where the key,value pair is stored.
     * @param key to look for
     * @return the index where the key is found or -1 if it is not found
     */
    public int getIndexOfKey(Object key) {
        int entry = hash[Math.abs(key.hashCode() % hashSize())];
        while (entry != 0) {
            final int idx = hash[entry];
            if (store[idx].equals(key)) { // found entry
                return idx;
            }
            entry = hash[entry + 1];
        }
        return -1;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (int i = 0; i < used; i++) {
            h += key(i).hashCode();
            V v = value(i);
            if (v != null) {
                h += v.hashCode();
            }
        }
        return h;
    }

    @Override
    public boolean equals(Object o) {
        if (! (o instanceof Hashlet) ) return false;
        Hashlet<?, ?> rhs = (Hashlet<?, ?>) o;
        if (used != rhs.used) return false;
        for (int i = 0; i < used; i++) {
            int bi = rhs.getIndexOfKey(key(i));
            if (bi == -1) return false;
            Object a = value(i);
            Object b = rhs.value(bi);
            boolean equal = (a == null) ? b == null : a.equals(b);
            if ( !equal ) return false;
        }
        return true;
    }

}

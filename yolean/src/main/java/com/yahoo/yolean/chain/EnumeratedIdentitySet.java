// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A set using identity comparison.
 * Keeps track of insertion order, which is available by calling insertionOrderedList.
 *
 * @author Tony Vaagenes
 */
class EnumeratedIdentitySet<T> implements Set<T> {

    private int counter = 0;
    private final Map<T, Integer> set = new IdentityHashMap<>();

    public EnumeratedIdentitySet(Collection<? extends T> collection) {
        addAll(collection);
    }

    public EnumeratedIdentitySet() {
        // empty
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return set.containsKey(o);
    }

    @Override
    public Iterator<T> iterator() {
        return set.keySet().iterator();
    }

    @Override
    public Object[] toArray() {
        return set.keySet().toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return set.keySet().toArray(a);
    }

    @Override
    public boolean add(T t) {
        if (set.containsKey(t)) {
            return false;
        } else {
            set.put(t, counter++);
            return true;
        }
    }

    @Override
    public boolean remove(Object o) {
        return set.remove(o) != null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return set.keySet().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> collection) {
        boolean changed = false;

        for (T t : collection) {
            changed |= add(t);
        }

        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
        return set.keySet().retainAll(collection);
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
        boolean changed = false;

        for (Object o : collection) {
            changed |= remove(o);
        }

        return changed;
    }

    @Override
    public void clear() {
        set.clear();
        counter = 0;
    }

    public List<T> insertionOrderedList() {
        if (set.isEmpty()) {
            counter = 0;
            return Collections.emptyList();
        }

        if (counter >= set.size() * 2 + 20) {
            renumber();
        }

        return getKeysSortedByValue(set, counter);
    }

    private static <KEY> List<KEY> getKeysSortedByValue(Map<KEY, Integer> set, int maxValue) {
        @SuppressWarnings("unchecked")
        KEY[] result = (KEY[])Array.newInstance(headKey(set).getClass(), maxValue);

        for (Map.Entry<KEY, Integer> entry : set.entrySet()) {
            result[entry.getValue()] = entry.getKey();
        }

        return removeNulls(result);
    }

    private static <T> T headKey(Map<T, ?> map) {
        return map.entrySet().iterator().next().getKey();
    }

    static <T> List<T> removeNulls(T[] list) {
        int insertionSpot = 0;
        for (int i = 0; i < list.length; i++) {
            T element = list[i];
            if (element != null) {
                list[insertionSpot] = element;
                insertionSpot++;
            }
        }
        return Arrays.asList(list).subList(0, insertionSpot);
    }

    //only for testing
    List<Integer> numbers() {
        return new ArrayList<>(set.values());
    }

    private void renumber() {
        SortedMap<Integer, T> invertedSet = invertedSortedMap(set);

        int i = 0;
        for (Map.Entry<Integer, T> entry : invertedSet.entrySet()) {
            set.put(entry.getValue(), i++);
        }
        counter = i;
    }

    private static <K, V> SortedMap<V, K> invertedSortedMap(Map<K, V> map) {
        SortedMap<V, K> result = new TreeMap<>();

        for (Map.Entry<K, V> entry : map.entrySet()) {
            result.put(entry.getValue(), entry.getKey());
        }

        return result;
    }

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class LazyMapTest {

    @Test
    public void requireThatInitialDelegateIsEmpty() {
        LazyMap<String, String> map = newLazyMap(new HashMap<String, String>());
        assertEquals(LazyMap.EmptyMap.class, map.getDelegate().getClass());
    }

    @Test
    public void requireThatEmptyMapPutUpgradesToSingletonMap() {
        LazyMap<String, String> map = newLazyMap(new HashMap<String, String>());
        assertNull(map.put("foo", "bar"));
        assertEquals(LazyMap.SingletonMap.class, map.getDelegate().getClass());

        map = newLazyMap(new HashMap<String, String>());
        map.putAll(Collections.singletonMap("foo", "bar"));
        assertEquals(LazyMap.SingletonMap.class, map.getDelegate().getClass());
    }

    @Test
    public void requireThatEmptyMapPutAllEmptyMapDoesNotUpgradeToSingletonMap() {
        LazyMap<String, String> map = newLazyMap(new HashMap<String, String>());
        map.putAll(Collections.<String, String>emptyMap());
        assertEquals(LazyMap.EmptyMap.class, map.getDelegate().getClass());
    }

    @Test
    public void requireThatEmptyMapPutAllUpgradesToFinalMap() {
        Map<String, String> delegate = new HashMap<>();
        LazyMap<String, String> map = newLazyMap(delegate);
        map.putAll(new HashMapBuilder<String, String>()
                           .put("foo", "bar")
                           .put("baz", "cox").map);
        assertSame(delegate, map.getDelegate());
        assertEquals(2, delegate.size());
        assertEquals("bar", delegate.get("foo"));
        assertEquals("cox", delegate.get("baz"));
    }

    @Test
    public void requireThatSingletonMapRemoveEntryDowngradesToEmptyMap() {
        LazyMap<String, String> map = newSingletonMap("foo", "bar");
        assertEquals("bar", map.remove("foo"));
        assertEquals(LazyMap.EmptyMap.class, map.getDelegate().getClass());
    }

    @Test
    public void requireThatSingletonMapRemoveUnknownDoesNotDowngradesToEmptyMap() {
        LazyMap<String, String> map = newSingletonMap("foo", "bar");
        assertNull(map.remove("baz"));
        assertEquals(LazyMap.SingletonMap.class, map.getDelegate().getClass());
    }

    @Test
    public void requireThatSingletonMapValueMayBeChangedInPlace() {
        LazyMap<String, String> map = newSingletonMap("foo", "bar");
        Map<String, String> delegate = map.getDelegate();
        assertEquals("bar", map.put("foo", "baz"));
        assertEquals("baz", map.get("foo"));
        assertSame(delegate, map.getDelegate());
        map.putAll(Collections.singletonMap("foo", "cox"));
        assertSame(delegate, map.getDelegate());
        assertEquals("cox", map.get("foo"));
    }

    @Test
    public void requireThatSingletonMapPutAllEmptyMapDoesNotUpgradeToFinalMap() {
        LazyMap<String, String> map = newSingletonMap("foo", "bar");
        map.putAll(Collections.<String, String>emptyMap());
        assertEquals(LazyMap.SingletonMap.class, map.getDelegate().getClass());
    }

    @Test
    public void requireThatSingletonMapPutUpgradesToFinalMap() {
        Map<String, String> delegate = new HashMap<>();
        LazyMap<String, String> map = newSingletonMap(delegate, "fooKey", "fooVal");
        map.put("barKey", "barVal");
        assertSame(delegate, map.getDelegate());
        assertEquals(2, delegate.size());
        assertEquals("fooVal", delegate.get("fooKey"));
        assertEquals("barVal", delegate.get("barKey"));
    }

    @Test
    public void requireThatSingletonMapPutAllUpgradesToFinalMap() {
        Map<String, String> delegate = new HashMap<>();
        LazyMap<String, String> map = newSingletonMap(delegate, "fooKey", "fooVal");
        map.putAll(new HashMapBuilder<String, String>()
                           .put("barKey", "barVal")
                           .put("bazKey", "bazVal").map);
        assertSame(delegate, map.getDelegate());
        assertEquals(3, delegate.size());
        assertEquals("fooVal", delegate.get("fooKey"));
        assertEquals("barVal", delegate.get("barKey"));
        assertEquals("bazVal", delegate.get("bazKey"));
    }

    @Test
    public void requireThatSingletonEntryIsMutable() {
        LazyMap<String, String> map = newSingletonMap("foo", "bar");
        Map.Entry<String, String> entry = map.entrySet().iterator().next();
        entry.setValue("baz");
        assertEquals("baz", map.get("foo"));
    }

    @Test
    public void requireThatSingletonEntryImplementsHashCode() {
        assertEquals(newSingletonMap("foo", "bar").entrySet().iterator().next().hashCode(),
                     newSingletonMap("foo", "bar").entrySet().iterator().next().hashCode());
    }

    @Test
    public void requireThatSingletonEntryImplementsEquals() {
        Map.Entry<String, String> map = newSingletonMap("foo", "bar").entrySet().iterator().next();
        assertNotEquals(map, null);
        assertNotEquals(map, new Object());
        assertEquals(map, map);
        assertNotEquals(map, newSingletonMap("baz", "cox").entrySet().iterator().next());
        assertNotEquals(map, newSingletonMap("foo", "cox").entrySet().iterator().next());
        assertEquals(map, newSingletonMap("foo", "bar").entrySet().iterator().next());
    }

    @Test
    public void requireThatSingletonEntrySetIteratorNextThrowsIfInvokedMoreThanOnce() {
        LazyMap<String, String> map = newSingletonMap("foo", "bar");
        Iterator<Map.Entry<String, String>> it = map.entrySet().iterator();
        it.next();
        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {

        }
        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {

        }
    }

    @Test
    public void requireThatSingletonEntrySetIteratorRemoveThrowsIfInvokedBeforeNext() {
        LazyMap<String, String> map = newSingletonMap("foo", "bar");
        Iterator<Map.Entry<String, String>> it = map.entrySet().iterator();
        try {
            it.remove();
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> makeMockMap() {
        return Mockito.mock(Map.class);
    }

    @Test
    public void requireThatMapDelegates() {
        Map<String, String> delegate = makeMockMap();
        Map<String, String> map = newLazyMap(delegate);
        map.put("foo", "bar");
        map.put("baz", "cox"); // trigger the assignment of the delegate
        Mockito.verify(delegate).put("foo", "bar");
        Mockito.verify(delegate).put("baz", "cox");

        Map<String, String> arg = Collections.singletonMap("baz", "cox");
        map.putAll(arg);
        Mockito.verify(delegate).putAll(arg);

        assertEquals(0, map.size());
        Mockito.verify(delegate).size();

        assertFalse(map.isEmpty());
        Mockito.verify(delegate).isEmpty();

        assertFalse(map.containsKey("foo"));
        Mockito.verify(delegate).containsKey("foo");

        assertFalse(map.containsValue("bar"));
        Mockito.verify(delegate).containsValue("bar");

        assertNull(map.get("foo"));
        Mockito.verify(delegate).get("foo");

        assertNull(map.remove("foo"));
        Mockito.verify(delegate).remove("foo");

        map.clear();
        Mockito.verify(delegate).clear();

        assertTrue(map.keySet().isEmpty());
        Mockito.verify(delegate).keySet();

        assertTrue(map.values().isEmpty());
        Mockito.verify(delegate).values();

        assertTrue(map.entrySet().isEmpty());
        Mockito.verify(delegate).entrySet();
    }

    @Test
    public void requireThatHashCodeIsImplemented() {
        assertEquals(newLazyMap(null).hashCode(),
                     newLazyMap(null).hashCode());
    }

    @Test
    public void requireThatEqualsIsImplemented() {
        Map<Object, Object> lhs = newLazyMap(new HashMap<>());
        Map<Object, Object> rhs = newLazyMap(new HashMap<>());
        assertEquals(lhs, lhs);
        assertEquals(lhs, rhs);

        Object key = new Object();
        Object val = new Object();
        lhs.put(key, val);
        assertEquals(lhs, lhs);
        assertFalse(lhs.equals(rhs));
        rhs.put(key, val);
        assertEquals(lhs, rhs);
    }

    @Test
    public void requireThatHashMapFactoryDelegatesToAHashMap() {
        LazyMap<String, String> map = LazyMap.newHashMap();
        map.put("foo", "bar");
        map.put("baz", "cox");
        assertEquals(HashMap.class, map.getDelegate().getClass());
    }

    private static <K, V> LazyMap<K, V> newSingletonMap(K key, V value) {
        return newSingletonMap(new HashMap<K, V>(), key, value);
    }

    private static <K, V> LazyMap<K, V> newSingletonMap(Map<K, V> delegate, K key, V value) {
        LazyMap<K, V> map = newLazyMap(delegate);
        map.put(key, value);
        return map;
    }

    private static <K, V> LazyMap<K, V> newLazyMap(final Map<K, V> delegate) {
        return new LazyMap<K, V>() {

            @Override
            protected Map<K, V> newDelegate() {
                return delegate;
            }
        };
    }

    private static class HashMapBuilder<K, V> {

        final Map<K, V> map = new HashMap<>();

        public HashMapBuilder<K, V> put(K key, V value) {
            map.put(key, value);
            return this;
        }
    }
}

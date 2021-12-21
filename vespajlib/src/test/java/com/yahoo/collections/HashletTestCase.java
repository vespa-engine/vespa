// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;

import static org.junit.Assert.*;

public class HashletTestCase {

    @Test
    public void testCopyEmptyHashlet() {
        Hashlet<String, Integer> hash = new Hashlet<>();
        Hashlet<String, Integer> hash2 = new Hashlet<>(hash);
        assertEquals(0, hash.size());
        assertEquals(0, hash2.size());
        hash.put("foo", 5);
        hash2.put("bar", 7);
        assertEquals(5, hash.get("foo").intValue());
        assertNull(hash.get("bar"));
        assertNull(hash2.get("foo"));
        assertEquals(7, hash2.get("bar").intValue());
    }

    private void verifyEquals(Object a, Object b) {
        assertEquals(a, b);
        assertEquals(b, a);
    }
    private void verifyNotEquals(Object a, Object b) {
        assertNotEquals(a, b);
        assertNotEquals(b, a);
    }

    @Test
    public void testThatDifferentGenericsDoesNotEqual() {
        Hashlet<Long, Long> a = new Hashlet<>();
        Hashlet<String, Integer> b = new Hashlet<>();
        verifyEquals(a, b);
        b.put("a", 1);
        verifyNotEquals(a, b);
        a.put(1L, 1L);
        verifyNotEquals(a, b);
    }
    @Test
    public void testHashCodeAndEquals() {
        Hashlet<String, Integer> h1 = new Hashlet<>();
        Hashlet<String, Integer> h2 = new Hashlet<>();
        assertEquals(h1.hashCode(), h2.hashCode());
        verifyEquals(h1, h2);

        h1.put("a", 7);
        assertNotEquals(h1.hashCode(), h2.hashCode());
        verifyNotEquals(h1, h2);

        h2.put("b", 8);
        assertNotEquals(h1.hashCode(), h2.hashCode());
        verifyNotEquals(h1, h2);

        h2.put("a", 7);
        assertNotEquals(h1.hashCode(), h2.hashCode());
        verifyNotEquals(h1, h2);

        h1.put("b", 8);
        assertEquals(h1.hashCode(), h2.hashCode());
        verifyEquals(h1, h2);

        h1.put("c", null);
        assertNotEquals(h1.hashCode(), h2.hashCode());
        verifyNotEquals(h1, h2);

        h2.put("d", null);
        assertNotEquals(h1.hashCode(), h2.hashCode());
        verifyNotEquals(h1, h2);

        h2.put("c", null);
        assertNotEquals(h1.hashCode(), h2.hashCode());
        verifyNotEquals(h1, h2);

        h1.put("d", null);
        assertEquals(h1.hashCode(), h2.hashCode());
        verifyEquals(h1, h2);
    }

    @Test
    public void testSetValue() {
        String A = "a";
        Hashlet<String, Integer> h = new Hashlet<>();
        h.put(A, 1);
        int indexOfA = h.getIndexOfKey(A);
        assertEquals(Integer.valueOf(1), h.value(indexOfA));
        h.setValue(indexOfA, 2);
        assertEquals(Integer.valueOf(2), h.value(indexOfA));
        assertEquals(Integer.valueOf(2), h.get(A));
    }

    @Test
    public void testGet() {
        Hashlet<String, Integer> h = new Hashlet<>();
        h.put("a", 1);
        h.put("b", null);
        assertEquals(0, h.getIndexOfKey("a"));
        assertEquals(h.get("a"), h.value(h.getIndexOfKey("a")));
        assertEquals(1, h.getIndexOfKey("b"));
        assertEquals(h.get("b"), h.value(h.getIndexOfKey("b")));
        assertEquals(-1, h.getIndexOfKey("c"));
        assertNull(h.get("c"));
    }

    @Test
    public void testCopyNonEmptyHashlet() {
        Hashlet<String, Integer> hash = new Hashlet<>();
        hash.put("foo", 5);
        hash.put("bar", 7);
        Hashlet<String, Integer> hash2 = new Hashlet<>(hash);
        assertEquals(2, hash2.size());
        assertEquals(5, hash2.get("foo").intValue());
        assertEquals(7, hash2.get("bar").intValue());
        assertEquals("foo", hash2.key(0));
        assertEquals("bar", hash2.key(1));
        assertEquals(5, hash2.value(0).intValue());
        assertEquals(7, hash2.value(1).intValue());
        assertSame(hash2.key(0), hash.key(0));
        assertSame(hash2.key(1), hash.key(1));
        assertSame(hash2.value(0), hash.value(0));
        assertSame(hash2.value(1), hash.value(1));
    }

    @Test
    public void testSetValueToNull() {
        Hashlet<String, Integer> hash = new Hashlet<>();
        hash.put("foo", 5);
        hash.put("bar", 7);
        assertEquals(2, hash.size());
        assertEquals(5, hash.get("foo").intValue());
        assertEquals(7, hash.get("bar").intValue());
        assertEquals("foo", hash.key(0));
        assertEquals("bar", hash.key(1));
        assertEquals(5, hash.value(0).intValue());
        assertEquals(7, hash.value(1).intValue());
        hash.put("foo", null);
        assertEquals(2, hash.size());
        assertNull(hash.get("foo"));
        assertEquals(7, hash.get("bar").intValue());
        assertEquals("foo", hash.key(0));
        assertEquals("bar", hash.key(1));
        assertNull(hash.value(0));
        assertEquals(7, hash.value(1).intValue());
    }

    @Test
    public void testIterate() {
        int n = 100;
        Hashlet<String, Integer> hash = new Hashlet<>();
        for (int i = 0; i < n; i++) {
            String str = ("" + i + "_str_" + i);
            hash.put(str, i);
        }
        assertEquals(n, hash.size());
        for (int i = 0; i < n; i++) {
            String str = ("" + i + "_str_" + i);
            assertEquals(str, hash.key(i));
            assertEquals(i, hash.value(i).intValue());
        }
    }

    @Test
    public void testManyEntries() {
        int n = 5000;
        Hashlet<String, Integer> hash = new Hashlet<>();
        for (int i = 0; i < n; i++) {
            String str = ("" + i + "_str_" + i);
            assertNull(hash.get(str));
            switch (i % 2) {
            case 1: assertNull(hash.put(str, i));
            }
        }
        assertEquals(n / 2, hash.size());
        for (int i = 0; i < n; i++) {
            String str = ("" + i + "_str_" + i);
            switch (i % 2) {
            case 0: assertNull(hash.get(str)); break;
            case 1: assertEquals(i, hash.get(str).intValue()); break;
            }
        }
    }
}

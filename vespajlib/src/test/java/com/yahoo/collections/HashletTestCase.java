// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class HashletTestCase {

    @Test
    public void testCopyEmptyHashlet() {
        Hashlet<String, Integer> hash = new Hashlet<>();
        Hashlet<String, Integer> hash2 = new Hashlet<>(hash);
        assertThat(hash.size(), is(0));
        assertThat(hash2.size(), is(0));
        hash.put("foo", 5);
        hash2.put("bar", 7);
        assertThat(hash.get("foo"), is(5));
        assertThat(hash.get("bar"), nullValue());
        assertThat(hash2.get("foo"), nullValue());
        assertThat(hash2.get("bar"), is(7));
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
        assertThat(hash2.size(), is(2));
        assertThat(hash2.get("foo"), is(5));
        assertThat(hash2.get("bar"), is(7));
        assertThat(hash2.key(0), is("foo"));
        assertThat(hash2.key(1), is("bar"));
        assertThat(hash2.value(0), is(5));
        assertThat(hash2.value(1), is(7));
        assertThat(hash2.key(0), sameInstance(hash.key(0)));
        assertThat(hash2.key(1), sameInstance(hash.key(1)));
        assertThat(hash2.value(0), sameInstance(hash.value(0)));
        assertThat(hash2.value(1), sameInstance(hash.value(1)));
    }

    @Test
    public void testSetValueToNull() {
        Hashlet<String, Integer> hash = new Hashlet<>();
        hash.put("foo", 5);
        hash.put("bar", 7);
        assertThat(hash.size(), is(2));
        assertThat(hash.get("foo"), is(5));
        assertThat(hash.get("bar"), is(7));
        assertThat(hash.key(0), is("foo"));
        assertThat(hash.key(1), is("bar"));
        assertThat(hash.value(0), is(5));
        assertThat(hash.value(1), is(7));
        hash.put("foo", null);
        assertThat(hash.size(), is(2));
        assertThat(hash.get("foo"), nullValue());
        assertThat(hash.get("bar"), is(7));
        assertThat(hash.key(0), is("foo"));
        assertThat(hash.key(1), is("bar"));
        assertThat(hash.value(0), nullValue());
        assertThat(hash.value(1), is(7));
    }

    @Test
    public void testIterate() {
        int n = 100;
        Hashlet<String, Integer> hash = new Hashlet<>();
        for (int i = 0; i < n; i++) {
            String str = ("" + i + "_str_" + i);
            hash.put(str, i);
        }
        assertThat(hash.size(), is(n));
        for (int i = 0; i < n; i++) {
            String str = ("" + i + "_str_" + i);
            assertThat(hash.key(i), is(str));
            assertThat(hash.value(i), is(i));
        }
    }

    @Test
    public void testManyEntries() {
        int n = 5000;
        Hashlet<String, Integer> hash = new Hashlet<>();
        for (int i = 0; i < n; i++) {
            String str = ("" + i + "_str_" + i);
            assertThat(hash.get(str), nullValue());
            switch (i % 2) {
            case 1: assertThat(hash.put(str, i), nullValue());
            }
        }
        assertThat(hash.size(), is(n / 2));
        for (int i = 0; i < n; i++) {
            String str = ("" + i + "_str_" + i);
            switch (i % 2) {
            case 0: assertThat(hash.get(str), nullValue()); break;
            case 1: assertThat(hash.get(str), is(i)); break;
            }
        }
    }
}

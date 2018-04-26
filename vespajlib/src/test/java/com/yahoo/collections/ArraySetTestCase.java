// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

/**
 * Check ArraySet seems to work. :)
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public final class ArraySetTestCase {

    @Test
    public void testAdd() {
        final String string = "abc";
        final String a = new String(string);
        final String b = new String(string);
        final ArraySet<String> t = new ArraySet<>(3);
        t.add(a);
        t.add(b);
        assertEquals(1, t.size());
        t.add(string);
        assertEquals(1, t.size());
        t.add("abd");
        t.add("abc");
        t.add("abd");
        assertEquals(2, t.size());

    }

    @Test
    public void testAddAll() {
        final List<String> stuff = doubleAdd();
        final ArraySet<String> t = new ArraySet<>(
                stuff.size());
        t.addAll(stuff);
        assertEquals(stuff.size() / 2, t.size());
    }

    private List<String> doubleAdd() {
        final List<String> stuff = new ArrayList<>();
        stuff.add("abc");
        stuff.add("abd");
        stuff.add("abe");
        stuff.add("abc");
        stuff.add("abd");
        stuff.add("abe");
        return stuff;
    }

    @Test
    public void testContains() {
        final String string = "abc";
        final String a = new String(string);
        final String b = new String(string);
        final ArraySet<String> t = new ArraySet<>(2);
        t.add(string);
        t.add(a);
        assertEquals(1, t.size());
        assertTrue(t.contains(a));
        assertTrue(t.contains(string));
        assertTrue(t.contains(b));
    }

    @Test
    public void testContainsAll() {
        final String string = "abc";
        final String a = new String(string);
        final String b = new String(string);
        final String c = "c";
        final List<String> stuff = new ArrayList<>();
        stuff.add(string);
        stuff.add(a);
        stuff.add(b);
        final ArraySet<String> t = new ArraySet<>(
                stuff.size());
        t.addAll(stuff);
        assertTrue(t.containsAll(stuff));
        stuff.add(c);
        assertFalse(t.containsAll(stuff));
    }

    @Test
    public void testRemove() {
        final String string = "abc";
        final String a = new String(string);
        final String b = new String(string);
        final ArraySet<String> t = new ArraySet<>(2);
        t.add("abc");
        t.add("abd");
        t.add("abe");
        assertEquals(3, t.size());
        assertFalse(t.remove("ab"));
        assertTrue(t.remove("abd"));
        assertFalse(t.remove("abd"));
        assertEquals(2, t.size());
        assertTrue(t.remove("abe"));
        assertFalse(t.remove("abe"));
        assertTrue(t.remove("abc"));
        assertTrue(t.isEmpty());
    }

    @Test
    public void testRetainAll() {
        final List<String> stuff = doubleAdd();
        final ArraySet<String> t = new ArraySet<>(
                stuff.size());
        t.addAll(stuff);
        assertFalse(t.retainAll(stuff));
        assertEquals(stuff.size() / 2, t.size());
        t.add("nalle");
        assertEquals(stuff.size() / 2 + 1, t.size());
        assertTrue(t.retainAll(stuff));
        assertEquals(stuff.size() / 2, t.size());
    }

    @Test
    public void testToArrayTArray() {
        final List<String> stuff = doubleAdd();
        final ArraySet<String> t = new ArraySet<>(
                stuff.size());
        t.addAll(stuff);
        final String[] s = t.toArray(new String[0]);
        assertEquals(t.size(), s.length);
        assertEquals(stuff.size() / 2, s.length);
    }

    @Test
    public void testGrow() {
        final ArraySet<Integer> t = new ArraySet<>(5);
        final int targetSize = 100;
        for (int i = 0; i < targetSize; ++i) {
            t.add(i);
        }
        assertEquals(targetSize, t.size());
        int n = 0;
        for (final Iterator<Integer> i = t.iterator(); i.hasNext();) {
            assertEquals(Integer.valueOf(n++), i.next());
        }
        assertEquals(targetSize, n);
    }

    @Test
    public void testBiggerRemoveAll() {
        final int targetSize = 100;
        final ArraySet<Integer> t = new ArraySet<>(
                targetSize);
        final Integer[] instances = new Integer[targetSize];
        final List<Integer> remove = buildSubSet(targetSize, t, instances);
        t.removeAll(remove);
        assertEquals(targetSize / 2, t.size());
        for (final Iterator<Integer> i = t.iterator(); i.hasNext();) {
            final Integer n = i.next();
            assertTrue(n % 2 == 0);
            assertFalse(remove.contains(n));

        }
    }

    @Test
    public void testBiggerRetainAll() {
        final int targetSize = 100;
        final ArraySet<Integer> t = new ArraySet<>(
                targetSize);
        final Integer[] instances = new Integer[targetSize];
        final List<Integer> retain = buildSubSet(targetSize, t, instances);
        t.retainAll(retain);
        assertEquals(targetSize / 2, t.size());
        for (final Iterator<Integer> i = t.iterator(); i.hasNext();) {
            final Integer n = i.next();
            assertTrue(n % 2 != 0);
            assertTrue(retain.contains(n));
        }
    }

    private List<Integer> buildSubSet(final int targetSize,
                                      final ArraySet<Integer> t, final Integer[] instances) {
        for (int i = 0; i < targetSize; ++i) {
            instances[i] = Integer.valueOf(i);
            t.add(instances[i]);
        }
        final List<Integer> subset = new ArrayList<>(50);
        for (int i = 0; i < targetSize; ++i) {
            if (i % 2 != 0) {
                subset.add(instances[i]);
            }
        }
        return subset;
    }

    @Test
    public void testMuckingAbout() {
        final int targetSize = 100;
        final ArraySet<Integer> t = new ArraySet<>(3);
        final Integer[] instances = new Integer[targetSize];
        final List<Integer> retain = buildSubSet(targetSize, t, instances);
        for (final Integer n : retain) {
            t.remove(n);
            assertEquals(targetSize - 1, t.size());
            t.add(n);
            assertEquals(targetSize, t.size());
        }
        assertEquals(targetSize, t.size());
        final Integer[] contents = t.toArray(new Integer[0]);
        Arrays.sort(contents, 0, targetSize);
        for (int i = 0; i < targetSize; ++i) {
            assertEquals(instances[i], contents[i]);
        }
    }

    @Test
    public void testMoreDuplicates() {
        final int targetSize = 100;
        final ArraySet<Integer> t = new ArraySet<>(3);
        final Integer[] instances = new Integer[targetSize];
        final List<Integer> add = buildSubSet(targetSize, t, instances);
        assertEquals(targetSize, t.size());
        t.addAll(add);
        assertEquals(targetSize, t.size());
    }

    @Test
    public void testEmptySet() {
        final int targetSize = 100;
        final ArraySet<Integer> t = new ArraySet<>(0);
        final Integer[] instances = new Integer[targetSize];
        final List<Integer> add = buildSubSet(targetSize, t, instances);
        for (Integer i : instances) {
            t.remove(i);
        }
        assertEquals(0, t.size());
        for (Integer i : add) {
            t.add(i);
        }
        assertEquals(targetSize / 2, t.size());
    }

    @Test
    public void testSmallEmptySet() {
        final ArraySet<Integer> t = new ArraySet<>(3);
        Integer a = 0;
        Integer b = 1;
        Integer c = 2;
        t.add(a);
        t.add(b);
        t.add(c);
        assertEquals(3, t.size());
        t.remove(a);
        assertEquals(2, t.size());
        t.remove(c);
        assertEquals(1, t.size());
        t.remove(c);
        assertEquals(1, t.size());
        t.remove(b);
        assertEquals(0, t.size());
        t.add(b);
        assertEquals(1, t.size());
        t.add(b);
        assertEquals(1, t.size());
        t.add(a);
        assertEquals(2, t.size());
        t.add(a);
        assertEquals(2, t.size());
        t.add(c);
        assertEquals(3, t.size());
        t.add(c);
        assertEquals(3, t.size());
    }

    @Test
    public void testIterator() {
        final int targetSize = 100;
        final ArraySet<Integer> t = new ArraySet<>(0);
        final Integer[] instances = new Integer[targetSize];
        final List<Integer> remove = buildSubSet(targetSize, t, instances);
        int traversed = 0;
        for (Iterator<Integer> i = t.iterator(); i.hasNext();) {
            Integer n = i.next();
            if (remove.contains(n)) {
                i.remove();
            }
            ++traversed;
        }
        assertEquals(targetSize, traversed);
        assertEquals(targetSize / 2, t.size());
        for (int i = 0; i < instances.length; ++i) {
            Integer n = instances[i];
            if (remove.contains(n)) {
                assertFalse(t.contains(n));
            } else {
                assertTrue(t.contains(n));
            }
        }
    }
}


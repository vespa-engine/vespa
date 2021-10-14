// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for EnumeratedIdentitySet.
 */
public class EnumeratedIdentitySetTest {

    private final List<Element> elements;

    public EnumeratedIdentitySetTest() {
        elements = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            elements.add(new Element());
        }
    }

    @Test
    public void size() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);
        assertEquals(elements.size(), set.size());

        set.add(elements.get(0));
        assertEquals(elements.size(), set.size());

        set.remove(elements.get(0));
        assertEquals(elements.size() - 1, set.size());
    }

    @Test
    public void isEmpty() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>();
        assertTrue(set.isEmpty());

        set.add(elements.get(0));
        assertFalse(set.isEmpty());
    }

    @Test
    public void contains2() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);
        assertTrue(set.contains(elements.get(0)));
        assertFalse(set.contains(new Element()));
    }

    @Test
    public void iterator() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);

        IdentityHashMap<Element, Void> collectedElements = new IdentityHashMap<>();
        int count = 0;
        for (Element element : set) {
            collectedElements.put(element, null);
            count++;
        }

        assertEquals(count, collectedElements.size());
        assertEquals(elements.size(), collectedElements.size());

        for (Element element : elements) {
            assertTrue(collectedElements.containsKey(element));
        }
    }
    private static boolean containsSame(Object a, Collection<?> coll) {
        for (Object b : coll) {
            if (a == b) return true;
        }
        return false;
    }
    private static boolean containsSubsetSame(Collection<?> subSet, Collection<?> superSet) {
        for (Object a : subSet) {
            if ( ! containsSame(a, superSet)) return false;
        }
        return true;
    }

    private static boolean containsAllSame(Collection<?> a, Collection<?> b) {
        return containsSubsetSame(a, b) && containsSubsetSame(b, a);
    }

    private static <T> Set<T> toIdentitySet(Collection<? extends T> collection) {
        Set<T> identitySet = Collections.newSetFromMap(new IdentityHashMap<>());
        identitySet.addAll(collection);
        return identitySet;
    }

    @Test
    public void toArray() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);

        Object[] array = set.toArray();
        Element[] array2 = set.toArray(new Element[0]);

        assertTrue(set.containsAll(Arrays.asList(array)));
        assertTrue(containsAllSame(Arrays.asList(array), set));
        assertTrue(containsAllSame(Arrays.asList(array2), set));
    }

    @Test
    public void add() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>();
        assertTrue(set.add(elements.get(0)));
        assertFalse(set.add(elements.get(0)));
    }

    @Test
    public void remove() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);
        assertTrue(set.remove(elements.get(0)));
        assertFalse(set.remove(elements.get(0)));
    }

    @Test
    public void containsAll() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);
        assertTrue(set.containsAll(elements.subList(0, 7)));
        assertTrue(set.containsAll(elements));

        List<Element> extendedElements = new ArrayList<>(elements);
        extendedElements.add(new Element());
        assertFalse(set.containsAll(extendedElements));
    }

    @Test
    public void addAll() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>();
        set.addAll(elements);

        assertTrue(containsAllSame(set, elements));
    }

    @Test
    public void retainAll() {
        Set<Element> set = new EnumeratedIdentitySet<>();

        set.addAll(elements.subList(0, 5));
        boolean changed = set.retainAll(toIdentitySet(elements.subList(3, 10)));

        assertTrue(changed);
        assertTrue(containsAllSame(set, elements.subList(3, 5)));

        changed = set.retainAll(toIdentitySet(elements));
        assertFalse(changed);
    }

    @Test
    public void removeAll() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);
        set.removeAll(elements.subList(0, 5));
        assertTrue(containsAllSame(set, elements.subList(5, 10)));
    }

    @Test
    public void clear() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);
        set.clear();
        assertTrue(set.isEmpty());
    }

    @Test
    public void removeNulls() {
        Element[] singletonArray = { null, elements.get(0), null };
        assertTrue(containsAllSame(EnumeratedIdentitySet.removeNulls(singletonArray),
                   Arrays.asList(elements.get(0))));

        Element[] elementsWithNull = new Element[20];

        Iterator<Element> iterator = elements.iterator();

        copyElementsTo(iterator, elementsWithNull, 2, 1);
        copyElementsTo(iterator, elementsWithNull, 4, 8);
        copyElementsTo(iterator, elementsWithNull, 19, 1);

        assertTrue(containsAllSame(EnumeratedIdentitySet.removeNulls(elementsWithNull), elements));
    }

    private void copyElementsTo(Iterator<Element> iterator, Element[] array, int startIndex, int numItems) {
        for (int i = 0; i < numItems; i++) {
            array[i + startIndex] = iterator.next();
        }
    }

    @Test
    public void renumber_preserves_ordering() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>();

        for (int i = 0; i < 200; i++) {
            set.add(new Element());
        }

        set.addAll(elements);

        EnumeratedIdentitySet<Element> elementsToPreserve = new EnumeratedIdentitySet<>(elements);

        for (Iterator<Element> i = set.iterator(); i.hasNext(); ) {
            if (!elementsToPreserve.contains(i.next())) {
                i.remove();
            }
        }

        List<Element> forceRenumber = set.insertionOrderedList();
        assertEquals(elements.size(), forceRenumber.size());

        for (int i = 0; i < elements.size(); i++) {
            assertSame(forceRenumber.get(i), elements.get(i));
        }

        set.add(new Element());
        assertTrue(containsAllSame(set.numbers(), range(0, 10)));
    }

    @Test
    public void renumber_when_empty() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);
        for (Iterator<Element> i = set.iterator(); i.hasNext(); ) {
            i.next();
            i.remove();
        }

        set.insertionOrderedList();
        assertTrue(set.numbers().isEmpty());

        set.add(new Element());
        assertTrue(containsAllSame(set.numbers(), singleton(0)));
    }

    private List<Integer> range(int start, int endInclusive) {
        List<Integer> result = new ArrayList<>();
        for (int i = start; i <= endInclusive; i++) {
            result.add(i);
        }

        return result;
    }

    static class Element {

        @Override
        public int hashCode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "Element@" + System.identityHashCode(this);
        }
    }
}

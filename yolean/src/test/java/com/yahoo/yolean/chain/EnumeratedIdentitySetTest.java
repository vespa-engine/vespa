// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.yahoo.yolean.chain.ContainsSameElements.containsSameElements;
import static com.yahoo.yolean.chain.ContainsSameElements.toIdentitySet;
import static java.util.Collections.singleton;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
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
        assertThat(set.size(), is(elements.size()));

        set.add(elements.get(0));
        assertThat(set.size(), is(elements.size()));

        set.remove(elements.get(0));
        assertThat(set.size(), is(elements.size() - 1));
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

        assertThat(collectedElements.size(), is(count));
        assertThat(collectedElements.size(), is(elements.size()));

        for (Element element : elements) {
            assertTrue(collectedElements.containsKey(element));
        }
    }

    @Test
    public void toArray() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);

        Object[] array = set.toArray();
        Element[] array2 = set.toArray(new Element[0]);

        assertThat(Arrays.asList(array), containsSameElements(set));
        assertThat(Arrays.asList(array2), containsSameElements(set));
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

        assertThat(set, containsSameElements(elements));
    }

    @Test
    public void retainAll() {
        Set<Element> set = new EnumeratedIdentitySet<>();

        set.addAll(elements.subList(0, 5));
        boolean changed = set.retainAll(toIdentitySet(elements.subList(3, 10)));

        assertTrue(changed);
        assertThat(set, containsSameElements(elements.subList(3, 5)));

        changed = set.retainAll(toIdentitySet(elements));
        assertFalse(changed);
    }

    @Test
    public void removeAll() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);
        set.removeAll(elements.subList(0, 5));
        assertThat(set, containsSameElements(elements.subList(5, 10)));
    }

    @Test
    public void clear() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);
        set.clear();
        assertThat(set, empty());
    }

    @Test
    public void removeNulls() {
        Element[] singletonArray = { null, elements.get(0), null };
        assertThat(EnumeratedIdentitySet.removeNulls(singletonArray),
                   containsSameElements(Arrays.asList(elements.get(0))));

        Element[] elementsWithNull = new Element[20];

        Iterator<Element> iterator = elements.iterator();

        copyElementsTo(iterator, elementsWithNull, 2, 1);
        copyElementsTo(iterator, elementsWithNull, 4, 8);
        copyElementsTo(iterator, elementsWithNull, 19, 1);

        assertThat(EnumeratedIdentitySet.removeNulls(elementsWithNull), containsSameElements(elements));
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
        assertThat(forceRenumber.size(), is(elements.size()));

        for (int i = 0; i < elements.size(); i++) {
            assertSame(forceRenumber.get(i), elements.get(i));
        }

        set.add(new Element());
        assertThat(set.numbers(), containsSameElements(range(0, 10)));
    }

    @Test
    public void renumber_when_empty() {
        EnumeratedIdentitySet<Element> set = new EnumeratedIdentitySet<>(elements);
        for (Iterator<Element> i = set.iterator(); i.hasNext(); ) {
            i.next();
            i.remove();
        }

        set.insertionOrderedList();
        assertThat(set.numbers(), empty());

        set.add(new Element());
        assertThat(set.numbers(), containsSameElements(singleton(0)));
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

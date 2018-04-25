// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests that PeekableListIterator behaves as expected.
 *
 * @author Einar M R Rosenvinge
 */
public class PeekableListIteratorTestCase {

    private List<String> strings;

    @Before
    public void setUp() {
        strings = new ArrayList<>();
        strings.add("0");
        strings.add("1");
        strings.add("2");
        strings.add("3");
        strings.add("4");
        strings.add("5");
    }

    @Test
    public void testSimpleListIterator() {
        ListIterator<String> it = strings.listIterator();
        assertEquals("0", it.next());
        assertEquals("1", it.next());
        //cursor is before "2"
        assertEquals("1", it.previous());
        //cursor is before "1"
        assertEquals("0", it.previous());
        //cursor is before "0"
        //removing 0, it's the last one returned by next() or previous():
        it.remove();
        assertEquals("1", it.next());
    }

    @Test
    public void testVarious() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        assertTrue(it.hasNext());
        assertEquals("0", it.peek());
        assertTrue(it.hasNext());
        assertEquals("0", it.next());
        assertEquals("1", it.next());
        assertEquals("2", it.next());
        assertEquals("3", it.peek());
        assertEquals("3", it.peek());
        assertEquals("3", it.peek());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertEquals("3", it.next());
        //cursor is now before 4
        it.add("banana");
        it.add("apple");
        //added before 4
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertEquals("4", it.peek());
        assertEquals("4", it.peek());
        assertEquals("4", it.peek());
        assertEquals("4", it.peek());
        //removing 3
        it.remove();
        assertEquals("4", it.peek());
        assertEquals("4", it.next());
        //replacing 4 with orange:
        it.set("orange");
        assertEquals("5", it.peek());
        assertEquals("5", it.next());
        assertFalse(it.hasNext());
        assertNull(it.peek());
        try {
            it.next();
            fail("Shouldn't have worked.");
        } catch (NoSuchElementException nsee) {
            // empty
        }
    }

    @Test
    public void testRemoveFirst() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());
        try {
            it.remove();  //this shouldn't work
            fail("shouldn't work");
        } catch (IllegalStateException ise) {
            //nada
        }

        assertEquals("0", it.next());
        it.remove();

        assertEquals(5, strings.size());
        assertEquals("1", strings.get(0));
    }

    @Test
    public void testPeekThenRemove() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        it.peek();
        try {
            it.remove();  //this should not work!
            fail("should have gotten exception");
        } catch (IllegalStateException ise) {
            //niks
        }

        assertEquals("0", it.next());
        it.remove();  //this should work
        assertEquals(5, strings.size());
        assertEquals("1", strings.get(0));
        assertEquals("2", strings.get(1));
        assertEquals("3", strings.get(2));
        assertEquals("4", strings.get(3));
        assertEquals("5", strings.get(4));
    }

    @Test
    public void testPeekNextRemove() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        assertEquals("0", it.peek());
        assertEquals("0", it.next());
        it.remove();
        assertEquals(5, strings.size());
        assertEquals("1", strings.get(0));
        assertEquals("2", strings.get(1));
        assertEquals("3", strings.get(2));
        assertEquals("4", strings.get(3));
        assertEquals("5", strings.get(4));
    }

    @Test
    public void testPeekNextPeekRemove() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        assertEquals("0", it.peek());
        assertEquals("0", it.next());
        assertEquals("1", it.peek());
        it.remove();
        assertEquals(5, strings.size());
        assertEquals("1", strings.get(0));
        assertEquals("2", strings.get(1));
        assertEquals("3", strings.get(2));
        assertEquals("4", strings.get(3));
        assertEquals("5", strings.get(4));
    }

    @Test
    public void testPeekNextNextRemove() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        assertEquals("0", it.peek());
        assertEquals("0", it.next());
        assertEquals("1", it.next());
        it.remove();
        assertEquals(5, strings.size());
        assertEquals("0", strings.get(0));
        assertEquals("2", strings.get(1));
        assertEquals("3", strings.get(2));
        assertEquals("4", strings.get(3));
        assertEquals("5", strings.get(4));
    }

    @Test
    public void testNextPeekRemove() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        assertEquals("0", it.next());
        assertEquals("1", it.peek());
        it.remove();
        assertEquals(5, strings.size());
        assertEquals("1", strings.get(0));
        assertEquals("2", strings.get(1));
        assertEquals("3", strings.get(2));
        assertEquals("4", strings.get(3));
        assertEquals("5", strings.get(4));
    }

    @Test
    public void testAddSimple() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        it.add("a");
        it.add("b");
        it.add("c");
        assertEquals("0", it.next());

        assertEquals(9, strings.size());
        assertEquals("a", strings.get(0));
        assertEquals("b", strings.get(1));
        assertEquals("c", strings.get(2));
        assertEquals("0", strings.get(3));
        assertEquals("1", strings.get(4));
        assertEquals("2", strings.get(5));
        assertEquals("3", strings.get(6));
        assertEquals("4", strings.get(7));
        assertEquals("5", strings.get(8));
    }

    @Test
    public void testPeekAdd() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        it.peek();
        it.add("a");
        it.add("b");
        it.add("c");
        assertEquals("0", it.next());

        assertEquals(9, strings.size());
        assertEquals("a", strings.get(0));
        assertEquals("b", strings.get(1));
        assertEquals("c", strings.get(2));
        assertEquals("0", strings.get(3));
        assertEquals("1", strings.get(4));
        assertEquals("2", strings.get(5));
        assertEquals("3", strings.get(6));
        assertEquals("4", strings.get(7));
        assertEquals("5", strings.get(8));
    }

    @Test
    public void testSetFirst() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());
        try {
            it.set("kjell");  //this shouldn't work
            fail("shouldn't work");
        } catch (IllegalStateException ise) {
            //nada
        }

        assertEquals("0", it.next());
        it.set("elvis");

        assertEquals(6, strings.size());
        assertEquals("elvis", strings.get(0));
    }

    @Test
    public void testPeekThenSet() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        it.peek();
        try {
            it.set("elvis");  //this should not work!
            fail("should have gotten exception");
        } catch (IllegalStateException ise) {
            //niks
        }

        assertEquals("0", it.next());
        it.set("presley");  //this should work
        assertEquals(6, strings.size());
        assertEquals("presley", strings.get(0));
        assertEquals("1", strings.get(1));
        assertEquals("2", strings.get(2));
        assertEquals("3", strings.get(3));
        assertEquals("4", strings.get(4));
        assertEquals("5", strings.get(5));
    }

    @Test
    public void testPeekNextSet() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        assertEquals("0", it.peek());
        assertEquals("0", it.next());
        it.set("buddy");
        assertEquals(6, strings.size());
        assertEquals("buddy", strings.get(0));
        assertEquals("1", strings.get(1));
        assertEquals("2", strings.get(2));
        assertEquals("3", strings.get(3));
        assertEquals("4", strings.get(4));
        assertEquals("5", strings.get(5));
    }

    @Test
    public void testPeekNextPeekSet() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        assertEquals("0", it.peek());
        assertEquals("0", it.next());
        assertEquals("1", it.peek());
        it.set("holly");
        assertEquals(6, strings.size());
        assertEquals("holly", strings.get(0));
        assertEquals("1", strings.get(1));
        assertEquals("2", strings.get(2));
        assertEquals("3", strings.get(3));
        assertEquals("4", strings.get(4));
        assertEquals("5", strings.get(5));
    }

    @Test
    public void testPeekNextNextSet() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        assertEquals("0", it.peek());
        assertEquals("0", it.next());
        assertEquals("1", it.next());
        it.set("jerry");
        assertEquals(6, strings.size());
        assertEquals("0", strings.get(0));
        assertEquals("jerry", strings.get(1));
        assertEquals("2", strings.get(2));
        assertEquals("3", strings.get(3));
        assertEquals("4", strings.get(4));
        assertEquals("5", strings.get(5));
    }

    @Test
    public void testNextPeekSet() {
        PeekableListIterator<String> it = new PeekableListIterator<String>(strings.listIterator());

        assertEquals("0", it.next());
        assertEquals("1", it.peek());
        it.set("lee");
        assertEquals(6, strings.size());
        assertEquals("lee", strings.get(0));
        assertEquals("1", strings.get(1));
        assertEquals("2", strings.get(2));
        assertEquals("3", strings.get(3));
        assertEquals("4", strings.get(4));
        assertEquals("5", strings.get(5));
    }

}

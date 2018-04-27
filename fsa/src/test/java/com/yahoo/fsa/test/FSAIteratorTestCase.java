// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa.test;

import com.yahoo.fsa.FSA;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author geirst
 */
public class FSAIteratorTestCase {

    private FSA fsa;

    private FSA.State state;

    private List<String> expected;

    @Before
    public void setUp() {
        fsa = new FSA("src/test/fsa/test-iterator.fsa");
        state = fsa.getState();

        expected = new ArrayList<String>();

        expected.add("abacus");
        expected.add("abadan");
        expected.add("abaisse");
        expected.add("abdicate");
        expected.add("abdomen");
        expected.add("abdominous");
        expected.add("dachs");
        expected.add("dacia");
        expected.add("daciaa");
        expected.add("daciab");
        expected.add("dacite");
        expected.add("dacota");
    }

    private void checkIterator(int beginIdx, int endIdx, String prefix) {
        System.out.println("checkIterator(" + beginIdx + ", " + endIdx + ", " + prefix + ")");
        java.util.Iterator<FSA.Iterator.Item> i = fsa.iterator(state);
        for (; i.hasNext() && beginIdx < endIdx; ++beginIdx) {
            FSA.Iterator.Item item = i.next();
            System.out.println("item: " + item);
            String str = prefix + item.getString();
            String data = item.getDataString();
            System.out.println("str:  '" + expected.get(beginIdx) + "'.equals('" + str + "')?");
            assertTrue(expected.get(beginIdx).equals(str));
            System.out.println("data: '" + expected.get(beginIdx) + "'.equals('" + data + "')?");
            assertTrue(expected.get(beginIdx).equals(data));
        }
        assertFalse(i.hasNext());
        assertTrue(beginIdx == endIdx);
    }

    @Test
    public void testIterator() {
        checkIterator(0, expected.size(), "");
    }

    @Test
    public void testIteratorSingle() {
        state.delta("dach");
        checkIterator(6, 7, "dach");
    }

    @Test
    public void testIteratorSubset() {
        state.delta("abd");
        checkIterator(3, 6, "abd");
    }

    @Test
    public void testIteratorFinalState() {
        state.delta("dacia");
        checkIterator(7, 10, "dacia");
    }

    @Test
    public void testIteratorFinalStateOnly() {
        state.delta("dachs");
        checkIterator(6, 7, "dachs");
    }

    @Test
    public void testIteratorEmpty1() {
        state.delta("b");
        java.util.Iterator i = fsa.iterator(state);
        assertFalse(i.hasNext());
        try {
            i.next();
            assertFalse(true);
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testIteratorEmpty2() {
        state.delta("daciac");
        java.util.Iterator i = fsa.iterator(state);
        assertFalse(i.hasNext());
        try {
            i.next();
            assertFalse(true);
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testIteratorRemove() {
        java.util.Iterator i = fsa.iterator(state);
        try {
            i.remove();
            assertFalse(true);
        } catch (UnsupportedOperationException e) {
            assertTrue(true);
        }
    }

}

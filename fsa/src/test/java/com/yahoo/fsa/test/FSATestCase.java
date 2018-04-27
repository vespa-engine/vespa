// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa.test;

import com.yahoo.fsa.FSA;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class FSATestCase {

    private FSA fsa;

    private FSA.State state;

    @Before
    public void setUp() throws IOException {
        fsa=new FSA(new FileInputStream("src/test/fsa/test-fsa.fsa"));
        state=fsa.getState();
    }

    @Test
    public void testSingleWordDelta() {
        state.delta("aword");
        assertTrue(state.isValid());
        assertTrue(state.isFinal());
    }

    @Test
    public void testSingleWordDeltaWord() {
        state.deltaWord("aword");
        assertTrue(state.isValid());
        assertTrue(state.isFinal());
    }

    @Test
    public void testSingleWordDeltaPartialMatch() {
        state.delta("awo");
        assertTrue(state.isValid());
        assertFalse(state.isFinal());
    }

    @Test
    public void testSingleWordDeltaPartialMatchWord() {
        state.deltaWord("awo");
        assertTrue(state.isValid());
        assertFalse(state.isFinal());
    }

    @Test
    public void testMultiWordDelta() {
        state.delta("th");
        assertFalse(state.isFinal());
        state.delta("is ");
        assertFalse(state.isFinal());
        state.delta("is ");
        assertFalse(state.isFinal());
        state.delta("a");
        assertFalse(state.isFinal());
        state.delta(" test");
        assertTrue(state.isValid());
        assertTrue(state.isFinal());
    }

    @Test
    public void testMultiWordDeltaWord() {
        state.deltaWord("this");
        assertFalse(state.isFinal());
        state.deltaWord("is");
        assertFalse(state.isFinal());
        state.deltaWord("a");
        assertFalse(state.isFinal());
        state.deltaWord("test");
        assertTrue(state.isValid());
        assertTrue(state.isFinal());
    }

    @Test
    public void testMultiWordDeltaWordInvalid() {
        state.deltaWord("th");
        assertFalse(state.isFinal());
        state.deltaWord("is ");
        assertFalse(state.isFinal());
        assertFalse(state.isValid());
    }

    @Test
    public void testMultiWordDeltaTry() {
        assertFalse(state.tryDeltaWord("thiss"));
        assertTrue(state.isValid());
        assertTrue(state.tryDeltaWord("this"));
        state.deltaWord("is");
        state.tryDeltaWord("a");
        assertFalse(state.tryDeltaWord("tes"));
        assertFalse(state.tryDeltaWord("tesz"));
        assertFalse(state.tryDeltaWord("teszzzz"));
        assertTrue(state.tryDeltaWord("test"));
        assertTrue(state.isValid());
        assertTrue(state.isFinal());
    }

}

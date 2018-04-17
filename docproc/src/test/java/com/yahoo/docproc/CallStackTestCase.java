// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests call stacks
 *
 * @author bratseth
 */
@SuppressWarnings("rawtypes")
public class CallStackTestCase {

    private CallStack callStack, insertStack;

    private Call call1, call2, call3, noCall, newCall, insert1, insert2, insert3;

    private DocumentProcessor processor2, noProcessor;

    @Before
    public void setUp() {
        callStack = new CallStack();
        call1 = new Call(new TestDocumentProcessor());
        processor2 = new TestDocumentProcessor();
        call2 = new Call(processor2);
        call3 = new Call(new TestDocumentProcessor());
        callStack.addLast(call1).addLast(call2).addLast(call3);
        noProcessor = new TestDocumentProcessor();
        noCall = new Call(noProcessor);
        newCall = new Call(new TestDocumentProcessor());

        insert1 = new Call(new TestDocumentProcessor());
        insert2 = new Call(new TestDocumentProcessor());
        insert3 = new Call(new TestDocumentProcessor());
        insertStack = new CallStack();
        insertStack.addLast(insert1).addLast(insert2).addLast(insert3);
    }

    @Test
    public void testFind() {
        assertSame(call2, callStack.findCall(processor2));
        assertSame(call2, callStack.findCall(processor2.getId()));
        assertNull(callStack.findCall(noProcessor));
        assertNull(callStack.findCall(noProcessor.getId()));
        assertNull(callStack.findCall(new TestDocumentProcessor()));
    }

    @Test
    public void testAddBefore() {
        callStack.addBefore(call2, newCall);
        Iterator i = callStack.iterator();
        assertEquals(call1, i.next());
        assertEquals(newCall, i.next());
        assertEquals(call2, i.next());
        assertEquals(call3, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testAddStackBefore() {
        callStack.addBefore(call2, insertStack);
        Iterator i = callStack.iterator();
        assertEquals(call1, i.next());
        assertEquals(insert1, i.next());
        assertEquals(insert2, i.next());
        assertEquals(insert3, i.next());
        assertEquals(call2, i.next());
        assertEquals(call3, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testAddAfter() {
        callStack.addAfter(call2, newCall);
        Iterator i = callStack.iterator();
        assertEquals(call1, i.next());
        assertEquals(call2, i.next());
        assertEquals(newCall, i.next());
        assertEquals(call3, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testAddStackAfter() {
        callStack.addAfter(call2, insertStack);
        Iterator i = callStack.iterator();
        assertEquals(call1, i.next());
        assertEquals(call2, i.next());
        assertEquals(insert1, i.next());
        assertEquals(insert2, i.next());
        assertEquals(insert3, i.next());
        assertEquals(call3, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testAddBeforeFirst() {
        callStack.addBefore(call1, newCall);
        Iterator i = callStack.iterator();
        assertEquals(newCall, i.next());
        assertEquals(call1, i.next());
        assertEquals(call2, i.next());
        assertEquals(call3, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testAddStackBeforeFirst() {
        callStack.addBefore(call1, insertStack);
        Iterator i = callStack.iterator();
        assertEquals(insert1, i.next());
        assertEquals(insert2, i.next());
        assertEquals(insert3, i.next());
        assertEquals(call1, i.next());
        assertEquals(call2, i.next());
        assertEquals(call3, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testAddAfterLast() {
        callStack.addAfter(call3, newCall);
        Iterator i = callStack.iterator();
        assertEquals(call1, i.next());
        assertEquals(call2, i.next());
        assertEquals(call3, i.next());
        assertEquals(newCall, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testAddStackAfterLast() {
        callStack.addAfter(call3, insertStack);
        Iterator i = callStack.iterator();
        assertEquals(call1, i.next());
        assertEquals(call2, i.next());
        assertEquals(call3, i.next());
        assertEquals(insert1, i.next());
        assertEquals(insert2, i.next());
        assertEquals(insert3, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testAddBeforeNonExisting() {
        callStack.addBefore(noCall, newCall);
        Iterator i = callStack.iterator();
        assertEquals(call1, i.next());
        assertEquals(call2, i.next());
        assertEquals(call3, i.next());
        assertEquals(newCall, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testAddStackBeforeNonExisting() {
        callStack.addBefore(noCall, insertStack);
        Iterator i = callStack.iterator();
        assertEquals(call1, i.next());
        assertEquals(call2, i.next());
        assertEquals(call3, i.next());
        assertEquals(insert1, i.next());
        assertEquals(insert2, i.next());
        assertEquals(insert3, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testAddAfterNonExisting() {
        callStack.addAfter(noCall, newCall);
        Iterator i = callStack.iterator();
        assertEquals(call1, i.next());
        assertEquals(call2, i.next());
        assertEquals(call3, i.next());
        assertEquals(newCall, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testAddStackAfterNonExisting() {
        callStack.addAfter(noCall, insertStack);
        Iterator i = callStack.iterator();
        assertEquals(call1, i.next());
        assertEquals(call2, i.next());
        assertEquals(call3, i.next());
        assertEquals(insert1, i.next());
        assertEquals(insert2, i.next());
        assertEquals(insert3, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testRemove() {
        callStack.remove(call1);
        Iterator i = callStack.iterator();
        assertEquals(call2, i.next());
        assertEquals(call3, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testRemoveNonExisting() {
        callStack.remove(noCall);
        Iterator i = callStack.iterator();
        assertEquals(call1, i.next());
        assertEquals(call2, i.next());
        assertEquals(call3, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testContains() {
        callStack.addLast(newCall);
        assertTrue(callStack.contains(call1));
        assertTrue(callStack.contains(call2));
        assertTrue(callStack.contains(call3));
        assertTrue(callStack.contains(newCall));
        assertFalse(callStack.contains(noCall));
    }

    @Test
    public void testPop() {
        assertEquals(call1, callStack.pop());
        assertEquals(call2, callStack.pop());
        callStack.addNext(newCall);

        assertFalse(callStack.contains(call1));
        assertFalse(callStack.contains(call2));
        assertTrue(callStack.contains(call3));
        assertTrue(callStack.contains(newCall));

        assertEquals(newCall, callStack.pop());
        assertTrue(callStack.contains(call3));
        assertFalse(callStack.contains(newCall));

        assertEquals(call3, callStack.pop());
        assertFalse(callStack.contains(call3));

        assertNull(callStack.pop());
    }

    @Test
    public void testGetLastPopped() {
        CallStack stakk = new CallStack();
        assertNull(stakk.getLastPopped());

        Call call;
        Call lastCall;

        call = callStack.pop();
        assertEquals(call1, call);
        lastCall = callStack.getLastPopped();
        assertEquals(call1, lastCall);
        assertEquals(call, lastCall);

        call = callStack.pop();
        assertEquals(call2, call);
        lastCall = callStack.getLastPopped();
        assertEquals(call2, lastCall);
        assertEquals(call, lastCall);

        call = callStack.pop();
        assertEquals(call3, call);
        lastCall = callStack.getLastPopped();
        assertEquals(call3, lastCall);
        assertEquals(call, lastCall);

        lastCall = callStack.getLastPopped();
        assertEquals(call3, lastCall);
        assertEquals(call, lastCall);

        lastCall = callStack.getLastPopped();
        assertEquals(call3, lastCall);
        assertEquals(call, lastCall);

        callStack.addLast(call1);
        callStack.addLast(call2);

        lastCall = callStack.getLastPopped();
        assertEquals(call3, lastCall);
        assertEquals(call, lastCall);

        call = callStack.pop();
        assertEquals(call1, call);
        lastCall = callStack.getLastPopped();
        assertEquals(call1, lastCall);
        assertEquals(call, lastCall);

        call = callStack.pop();
        assertEquals(call2, call);
        lastCall = callStack.getLastPopped();
        assertEquals(call2, lastCall);
        assertEquals(call, lastCall);

        lastCall = callStack.getLastPopped();
        assertEquals(call2, lastCall);
        assertEquals(call, lastCall);

        lastCall = callStack.getLastPopped();
        assertEquals(call2, lastCall);
        assertEquals(call, lastCall);
    }

    private static class TestDocumentProcessor extends com.yahoo.docproc.SimpleDocumentProcessor {
    }

}

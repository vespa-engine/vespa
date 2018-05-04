// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class EventBarrierTestCase {

    @Test
    public void testEmpty() {
        // waiting for an empty set of events
        Barrier b = new Barrier();
        EventBarrier eb = new EventBarrier();

        assertTrue(!eb.startBarrier(b));
        assertTrue(!b.done);
        assertEquals(eb.getNumEvents(), 0);
        assertEquals(eb.getNumBarriers(), 0);

        int token = eb.startEvent();
        eb.completeEvent(token);

        assertTrue(!eb.startBarrier(b));
        assertTrue(!b.done);
        assertEquals(eb.getNumEvents(), 0);
        assertEquals(eb.getNumBarriers(), 0);
    }

    @Test
    public void testSimple() {
        // a single barrier waiting for a single event
        Barrier b = new Barrier();
        EventBarrier eb = new EventBarrier();
        assertEquals(eb.getNumEvents(), 0);
        assertEquals(eb.getNumBarriers(), 0);

        int token = eb.startEvent();
        assertEquals(eb.getNumEvents(), 1);
        assertEquals(eb.getNumBarriers(), 0);

        assertTrue(eb.startBarrier(b));
        assertTrue(!b.done);
        assertEquals(eb.getNumEvents(), 1);
        assertEquals(eb.getNumBarriers(), 1);

        eb.completeEvent(token);
        assertTrue(b.done);
        assertEquals(eb.getNumEvents(), 0);
        assertEquals(eb.getNumBarriers(), 0);
    }

    @Test
    public void testBarrierChain() {
        // more than one barrier waiting for the same set of events
        Barrier b1 = new Barrier();
        Barrier b2 = new Barrier();
        Barrier b3 = new Barrier();
        EventBarrier eb = new EventBarrier();
        assertEquals(eb.getNumEvents(), 0);
        assertEquals(eb.getNumBarriers(), 0);

        int token = eb.startEvent();
        assertEquals(eb.getNumEvents(), 1);
        assertEquals(eb.getNumBarriers(), 0);

        assertTrue(eb.startBarrier(b1));
        assertTrue(eb.startBarrier(b2));
        assertTrue(eb.startBarrier(b3));
        assertTrue(!b1.done);
        assertTrue(!b2.done);
        assertTrue(!b3.done);

        assertEquals(eb.getNumEvents(), 1);
        assertEquals(eb.getNumBarriers(), 3);

        eb.completeEvent(token);
        assertTrue(b1.done);
        assertTrue(b2.done);
        assertTrue(b3.done);
        assertEquals(eb.getNumEvents(), 0);
        assertEquals(eb.getNumBarriers(), 0);
    }

    @Test
    public void testEventAfter() {
        // new events starting after the start of a barrier
        Barrier b = new Barrier();
        EventBarrier eb = new EventBarrier();
        assertEquals(eb.getNumEvents(), 0);
        assertEquals(eb.getNumBarriers(), 0);

        int token = eb.startEvent();
        assertEquals(eb.getNumEvents(), 1);
        assertEquals(eb.getNumBarriers(), 0);

        assertTrue(eb.startBarrier(b));
        assertTrue(!b.done);
        assertEquals(eb.getNumEvents(), 1);
        assertEquals(eb.getNumBarriers(), 1);

        int t2 = eb.startEvent();
        assertTrue(!b.done);
        assertEquals(eb.getNumEvents(), 2);
        assertEquals(eb.getNumBarriers(), 1);

        eb.completeEvent(token);
        assertTrue(b.done);
        assertEquals(eb.getNumEvents(), 1);
        assertEquals(eb.getNumBarriers(), 0);

        eb.completeEvent(t2);
        assertEquals(eb.getNumEvents(), 0);
        assertEquals(eb.getNumBarriers(), 0);
    }

    @Test
    public void testReorder() {
        // events completing in a different order than they started
        Barrier b1 = new Barrier();
        Barrier b2 = new Barrier();
        Barrier b3 = new Barrier();
        EventBarrier eb = new EventBarrier();

        int t1 = eb.startEvent();
        eb.startBarrier(b1);
        int t2 = eb.startEvent();
        eb.startBarrier(b2);
        int t3 = eb.startEvent();
        eb.startBarrier(b3);
        int t4 = eb.startEvent();

        assertEquals(eb.getNumEvents(), 4);
        assertEquals(eb.getNumBarriers(), 3);

        assertTrue(!b1.done);
        assertTrue(!b2.done);
        assertTrue(!b3.done);

        eb.completeEvent(t4);
        assertTrue(!b1.done);
        assertTrue(!b2.done);
        assertTrue(!b3.done);

        eb.completeEvent(t3);
        assertTrue(!b1.done);
        assertTrue(!b2.done);
        assertTrue(!b3.done);

        eb.completeEvent(t1);
        assertTrue(b1.done);
        assertTrue(!b2.done);
        assertTrue(!b3.done);

        eb.completeEvent(t2);
        assertTrue(b1.done);
        assertTrue(b2.done);
        assertTrue(b3.done);

        assertEquals(eb.getNumEvents(), 0);
        assertEquals(eb.getNumBarriers(), 0);
    }

    private static class Barrier implements EventBarrier.BarrierWaiter {
        boolean done = false;

        @Override
        public void completeBarrier() {
            done = true;
        }
    }

}

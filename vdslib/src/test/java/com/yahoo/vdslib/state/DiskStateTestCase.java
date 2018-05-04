// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

import org.junit.Test;

import java.text.ParseException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiskStateTestCase {

    private static final double delta = 0.0000000001;

    @Test
    public void testEquals() {
        DiskState d1 = new DiskState(State.UP, "", 1);
        DiskState d2 = new DiskState(State.UP, "", 2);
        DiskState d3 = new DiskState(State.DOWN, "Failed disk", 1);
        DiskState d4 = new DiskState(State.DOWN, "IO error", 1);
        DiskState d5 = new DiskState(State.UP, "", 1);
        DiskState d6 = new DiskState(State.UP, "", 2);
        DiskState d7 = new DiskState(State.DOWN, "Failed disk", 1);
        DiskState d8 = new DiskState(State.DOWN, "IO error", 1);

        assertEquals(d1, d5);
        assertEquals(d2, d6);
        assertEquals(d3, d7);
        assertEquals(d4, d8);

        assertFalse(d1.equals(d2));
        assertFalse(d1.equals(d3));
        assertFalse(d1.equals(d4));

        assertFalse(d2.equals(d1));
        assertFalse(d2.equals(d3));
        assertFalse(d2.equals(d4));

        assertFalse(d3.equals(d1));
        assertFalse(d3.equals(d2));
        assertEquals(d3, d4);

        assertFalse(d4.equals(d1));
        assertFalse(d4.equals(d2));
        assertEquals(d4, d3);

        assertFalse(d1.equals("class not instance of Node"));
    }

    @Test
    public void testSerialization() throws ParseException {
        DiskState d = new DiskState();
        DiskState other = new DiskState(d.serialize("", true));
        assertEquals(d, other);
        assertEquals(d.toString(), other.toString());
        assertEquals(State.UP, other.getState());
        assertEquals(1.0, other.getCapacity(), delta);
        assertEquals("", other.getDescription());
        assertEquals("s:u", d.serialize("", false));
        assertEquals("s:u", d.serialize("", true));
        assertEquals("", d.serialize(".0.", false));
        assertEquals("", d.serialize(".0.", true));

        assertEquals(d, new DiskState(": s:u sbadkey:somevalue cbadkey:somevalue mbadkey:somevalue unknwonkey:somevalue"));

        d = new DiskState(State.UP, "Slow disk", 1.0);
        other = new DiskState(d.serialize("", true));
        assertEquals(d, other);
        assertEquals(d.toString(), other.toString());
        assertEquals(State.UP, other.getState());
        assertEquals(1.0, other.getCapacity(), delta);
        assertEquals("Slow disk", other.getDescription());
        assertEquals("s:u", d.serialize("", false));
        assertEquals("s:u m:Slow\\x20disk", d.serialize("", true));
        assertEquals("", d.serialize(".0.", false));
        assertEquals(".0.m:Slow\\x20disk", d.serialize(".0.", true));

        d = new DiskState(State.DOWN, "Failed disk", 2.0);
        other = new DiskState(d.serialize("", true));
        assertEquals(d, other);
        assertEquals(d.toString(), other.toString());
        assertEquals(State.DOWN, other.getState());
        assertEquals(2.0, other.getCapacity(), delta);
        assertEquals("Failed disk", other.getDescription());
        assertEquals("s:d c:2.0", d.serialize("", false));
        assertEquals("s:d c:2.0 m:Failed\\x20disk", d.serialize("", true));
        assertEquals(".0.s:d .0.c:2.0", d.serialize(".0.", false));
        assertEquals(".0.s:d .0.c:2.0 .0.m:Failed\\x20disk", d.serialize(".0.", true));

        try {
            new DiskState(State.MAINTENANCE);
            assertTrue("Method expected to throw IllegalArgumentException", false);
        } catch (IllegalArgumentException e) {
            assertEquals("State " + State.MAINTENANCE + " is not a valid disk state.", e.getMessage());
        }
        try {
            new DiskState(State.UP, "", -1);
            assertTrue("Method expected to throw IllegalArgumentException", false);
        } catch (IllegalArgumentException e) {
            assertEquals("Negative capacity makes no sense.", e.getMessage());
        }
        try {
            new DiskState("nocolon");
            assertTrue("Method expected to throw ParseException", false);
        } catch (ParseException e) {
            assertEquals("Token nocolon does not contain ':': nocolon", e.getMessage());
        }
        try {
            new DiskState("s:d c:badvalue");
            assertTrue("Method expected to throw ParseException", false);
        } catch (ParseException e) {
            assertEquals("Illegal disk capacity 'badvalue'. Capacity must be a positive floating point number", e.getMessage());
        }
    }
}

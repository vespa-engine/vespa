// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import org.junit.Test;

import static org.junit.Assert.*;

public class VisitorOrderingTestCase {

    @Test
    public void testVisitorOrderingDefault() {
        VisitorOrdering ordering = new VisitorOrdering();
        assertEquals(VisitorOrdering.ASCENDING, ordering.getOrder());
        assertEquals(0, ordering.getDivisionBits());
        assertEquals(0, ordering.getWidthBits());
        assertEquals(0, ordering.getOrderingStart());
        assertEquals("+,0,0,0", ordering.toString());
    }

    @Test
    public void testVisitorOrderingAscending() {
        VisitorOrdering ordering = new VisitorOrdering(VisitorOrdering.ASCENDING);
        assertEquals(VisitorOrdering.ASCENDING, ordering.getOrder());
        assertEquals(0, ordering.getDivisionBits());
        assertEquals(0, ordering.getWidthBits());
        assertEquals(0, ordering.getOrderingStart());
        assertEquals("+,0,0,0", ordering.toString());
    }


    @Test
    public void testVisitorOrderingComplex() {
        VisitorOrdering ordering = new VisitorOrdering(VisitorOrdering.DESCENDING, (long)3, (short)2, (short)1);
        assertEquals(VisitorOrdering.DESCENDING, ordering.getOrder());
        assertEquals(1, ordering.getDivisionBits());
        assertEquals(2, ordering.getWidthBits());
        assertEquals(3, ordering.getOrderingStart());
        assertEquals("-,2,1,3", ordering.toString());
    }
}

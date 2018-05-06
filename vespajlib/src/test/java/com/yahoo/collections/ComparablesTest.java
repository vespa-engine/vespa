// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.collections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ComparablesTest {
    @Test
    public void testMinAndMax() {
        Integer i1 = 1;
        Integer i2 = 2;

        assertEquals(i1, Comparables.min(i1, i2));
        assertEquals(i1, Comparables.min(i2, i1));

        assertEquals(i2, Comparables.max(i1, i2));
        assertEquals(i2, Comparables.max(i2, i1));
    }
}
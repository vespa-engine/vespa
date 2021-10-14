// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Integrity test for representation of undefined numeric field values.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class NanNumberTestCase {


    @Test
    public final void testIntValue() {
        assertEquals(0, NanNumber.NaN.intValue());
    }

    @Test
    public final void testLongValue() {
        assertEquals(0L, NanNumber.NaN.longValue());
    }

    @Test
    public final void testFloatValue() {
        assertTrue(Float.isNaN(NanNumber.NaN.floatValue()));
    }

    @Test
    public final void testDoubleValue() {
        assertTrue(Double.isNaN(NanNumber.NaN.doubleValue()));
    }

    @Test
    public final void testToString() {
        assertEquals("", NanNumber.NaN.toString());
    }

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Integrity test for representation of undefined numeric field values.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class NanNumberTestCase {


    @Test
    final void testIntValue() {
        assertEquals(0, NanNumber.NaN.intValue());
    }

    @Test
    final void testLongValue() {
        assertEquals(0L, NanNumber.NaN.longValue());
    }

    @Test
    final void testFloatValue() {
        assertTrue(Float.isNaN(NanNumber.NaN.floatValue()));
    }

    @Test
    final void testDoubleValue() {
        assertTrue(Double.isNaN(NanNumber.NaN.doubleValue()));
    }

    @Test
    final void testToString() {
        assertEquals("", NanNumber.NaN.toString());
    }

}

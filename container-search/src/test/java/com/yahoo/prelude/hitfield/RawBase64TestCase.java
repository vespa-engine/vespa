// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.prelude.hitfield;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author baldersheim
 */
public class RawBase64TestCase {
    private static final byte [] first = {0, 1, 2};
    private static final byte [] second = {19, 0};
    private static final byte [] last = {-127, 0};
    private static final byte [] longer = {0, 1, 2, 3};
    @Test
    public void requireToStringToProvideBase64Encoding() {
        assertEquals("AAEC", new RawBase64(first).toString());
    }

    private void verify(int expected, byte [] a, byte [] b) {
        int diff = new RawBase64(a).compareTo(new RawBase64(b));
        if (expected == 0) {
            assertEquals(expected, diff);
        } else {
            assertEquals(expected, diff / Math.abs(diff));
        }
    }

    @Test
    public void testSortOrder() {
        verify(0, first, first);
        verify(-1, first, longer);
        verify(1, longer, first);

        verify(-1, first, second);
        verify(1, second, first);

        verify(-1, first, last);
        verify(1, last, first);
    }
}

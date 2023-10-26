// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class ByteArrayComparatorTestCase {
    @Test
    public void arrayLength() {
        byte[] shortArr = new byte[]{(byte) 1, (byte) 2};
        byte[] longArr = new byte[]{(byte) 0, (byte) 3, (byte) 3, (byte) 3, (byte) 3, (byte) 3};

        assertEquals(-1, ByteArrayComparator.compare(shortArr, longArr));
    }

    @Test
    public void compareArrays() {
        byte[] one = new byte[]{(byte) 1, (byte) 2, (byte) 3, (byte) 3, (byte) 3, (byte) 3};
        byte[] two = new byte[]{(byte) 0, (byte) 3, (byte) 3, (byte) 3, (byte) 3, (byte) 3};

        assertEquals(1, ByteArrayComparator.compare(one, two));
        assertEquals(-1, ByteArrayComparator.compare(two, one));
    }

    @Test
    public void compareEqualArrays() {
        byte[] one = new byte[]{(byte) 1, (byte) 2, (byte) 3, (byte) 3, (byte) 3, (byte) 3, (byte) 9};
        byte[] two = new byte[]{(byte) 1, (byte) 2, (byte) 3, (byte) 3, (byte) 3, (byte) 3, (byte) 9};

        assertEquals(0, ByteArrayComparator.compare(one, two));
        assertEquals(0, ByteArrayComparator.compare(two, one));
    }

}

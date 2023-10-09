// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class IntArrayComparatorTestCase {
    @Test
    public void arrayLength() {
        int[] shortArr = new int[]{1, 2};
        int[] longArr = new int[]{0, 3, 3, 3, 3, 3};

        assertEquals(-1, IntArrayComparator.compare(shortArr, longArr));
    }

    @Test
    public void compareArrays() {
        int[] one = new int[]{1, 2, 3, 3, 3, 3};
        int[] two = new int[]{0, 3, 3, 3, 3, 3};

        assertEquals(1, IntArrayComparator.compare(one, two));
        assertEquals(-1, IntArrayComparator.compare(two, one));
    }

    @Test
    public void compareEqualArrays() {
        int[] one = new int[]{1, 2, 3, 3, 3, 3, 9};
        int[] two = new int[]{1, 2, 3, 3, 3, 3, 9};

        assertEquals(0, IntArrayComparator.compare(one, two));
        assertEquals(0, IntArrayComparator.compare(two, one));
    }
}

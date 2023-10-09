// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class CollectionComparatorTestCase {
    @Test
    public void arrayLength() {
        List<String> shortArr = Arrays.asList("x", "y");
        List<String> longArr = Arrays.asList("a", "b", "c", "d", "e");

        assertEquals(-1, CollectionComparator.compare(shortArr, longArr));
    }

    @Test
    public void compareArrays() {
        List<String> one = Arrays.asList("b", "c", "d", "d", "e");
        List<String> two = Arrays.asList("a", "b", "c", "d", "e");

        assertEquals(1, CollectionComparator.compare(one, two));
        assertEquals(-1, CollectionComparator.compare(two, one));
    }

    @Test
    public void compareEqualArrays() {
        List<String> one = Arrays.asList("a", "b", "c", "d", "e");
        List<String> two = Arrays.asList("a", "b", "c", "d", "e");

        assertEquals(0, CollectionComparator.compare(one, two));
        assertEquals(0, CollectionComparator.compare(two, one));
    }
}

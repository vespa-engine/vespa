// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class PredicateSplitTestCase {
    @Test
    public void requireThatSplitWorks() {
        List<Integer> l = new ArrayList<Integer>();
        l.add(1);
        l.add(6);
        l.add(2);
        l.add(4);
        l.add(5);
        PredicateSplit<Integer> result = PredicateSplit.partition(l, x -> (x % 2 == 0));
        assertEquals((long) result.falseValues.size(), 2L);
        assertEquals((long) result.falseValues.get(0), 1L);
        assertEquals((long) result.falseValues.get(1), 5L);

        assertEquals((long) result.trueValues.size(), 3L);
        assertEquals((long) result.trueValues.get(0), 6L);
        assertEquals((long) result.trueValues.get(1), 2L);
        assertEquals((long) result.trueValues.get(2), 4L);
    }
}

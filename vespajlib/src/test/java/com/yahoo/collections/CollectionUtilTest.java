// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Tony Vaagenes
 */
public class CollectionUtilTest {
    List<Integer> l1 = Arrays.asList(1, 2, 4, 5, 6, 7);
    List<Integer> l2 = Arrays.asList(3, 4, 5, 6, 7);

    @Before
    public void shuffle() {
        Collections.shuffle(l1);
        Collections.shuffle(l2);
    }

    @Test
    public void testMkString() {
        assertEquals("1, 2, 3, 4",
                CollectionUtil.mkString(Arrays.asList(1, 2, 3, 4), ", "));
    }

    @Test
    public void testEqualContentsIgnoreOrder() {
        List<Integer> l2Copy = new ArrayList<>();
        l2Copy.addAll(l2);
        shuffle();
        assertTrue(CollectionUtil.equalContentsIgnoreOrder(
                l2, l2Copy));
        assertFalse(CollectionUtil.equalContentsIgnoreOrder(
                l1, l2));
    }

    @Test
    public void testSymmetricDifference() {
        assertTrue(CollectionUtil.equalContentsIgnoreOrder(
                Arrays.asList(1, 2, 3),
                CollectionUtil.symmetricDifference(l1, l2)));
    }
}

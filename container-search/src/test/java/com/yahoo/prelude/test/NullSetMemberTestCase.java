// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.test;

import org.junit.Test;

import java.util.HashSet;

/**
 * Tests null members in HashSet
 */
public class NullSetMemberTestCase extends junit.framework.TestCase {

    @Test
    public void testNullMember() {
        HashSet<?> s = new HashSet<>();
        assertEquals(s.size(), 0);
        assertFalse(s.contains(null));
        s.add(null);
        assertEquals(s.size(), 1);
        assertTrue(s.contains(null));
    }

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.test;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests null members in HashSet
 */
public class NullSetMemberTestCase {

    @Test
    void testNullMember() {
        HashSet<?> s = new HashSet<>();
        assertEquals(s.size(), 0);
        assertFalse(s.contains(null));
        s.add(null);
        assertEquals(s.size(), 1);
        assertTrue(s.contains(null));
    }

}

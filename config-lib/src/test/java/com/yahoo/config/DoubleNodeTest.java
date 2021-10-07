// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class DoubleNodeTest {
    @Test
    public void testSetValue() {
        DoubleNode n = new DoubleNode();
        assertFalse(n.doSetValue("invalid"));
        assertTrue(n.doSetValue("3.14"));
        assertEquals(3.14, n.value(), 0.001);
    }
}

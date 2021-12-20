// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class LongNodeTest {
    @Test
    public void testSetValue() {
        LongNode n = new LongNode();
        assertFalse(n.setValue("invalid"));
        assertTrue(n.setValue("10"));
        assertEquals(10L, n.value().longValue());
    }
}

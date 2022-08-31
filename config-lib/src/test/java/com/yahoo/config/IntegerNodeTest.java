// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class IntegerNodeTest {

    @Test
    void testSetValue() {
        IntegerNode n = new IntegerNode();
        assertFalse(n.setValue("invalid"));
        assertTrue(n.setValue("10"));
        assertEquals(10, n.value().intValue());
    }

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Ulf Lilleengen
 */
public class DoubleNodeTest {

    @Test
    void testSetValue() {
        DoubleNode n = new DoubleNode();
        assertFalse(n.doSetValue("invalid"));
        assertTrue(n.doSetValue("3.14"));
        assertEquals(3.14, n.value(), 0.001);
    }

}

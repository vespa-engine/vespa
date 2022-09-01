// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class BooleanNodeTest {

    @Test
    void testSetValue() {
        BooleanNode n = new BooleanNode();
        assertTrue(n.doSetValue("true"));
        assertTrue(n.doSetValue("TRUE"));
        assertTrue(n.doSetValue("false"));
        assertTrue(n.doSetValue("FALSE"));
        assertFalse(n.doSetValue("FALSEa"));
        assertFalse(n.doSetValue("aFALSE"));
    }

}

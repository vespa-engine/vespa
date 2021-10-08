// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class BooleanNodeTest {
    @Test
    public void testSetValue() {
        BooleanNode n = new BooleanNode();
        assertTrue(n.doSetValue("true"));
        assertTrue(n.doSetValue("TRUE"));
        assertTrue(n.doSetValue("false"));
        assertTrue(n.doSetValue("FALSE"));
        assertFalse(n.doSetValue("FALSEa"));
        assertFalse(n.doSetValue("aFALSE"));
    }
}

// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class LongNodeTest {
    @Test
    public void testSetValue() {
        LongNode n = new LongNode();
        assertFalse(n.setValue("invalid"));
        assertTrue(n.setValue("10"));
        assertThat(n.value(), is(10l));
    }
}

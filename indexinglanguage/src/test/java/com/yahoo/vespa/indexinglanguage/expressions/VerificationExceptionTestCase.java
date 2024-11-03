// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class VerificationExceptionTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Expression exp = new SimpleExpression();
        VerificationException e = new VerificationException(exp, "foo");
        assertEquals(exp.toString(), e.getExpression());
        assertEquals("foo", e.getMessage());
        assertTrue(e.toString().contains(exp.toString()));
        assertTrue(e.toString().contains(e.getMessage()));
    }
}

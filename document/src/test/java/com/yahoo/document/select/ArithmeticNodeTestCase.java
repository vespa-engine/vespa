// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.select.rule.ArithmeticNode;
import com.yahoo.document.select.rule.LiteralNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ArithmeticNodeTestCase {

    private void verify(Object expect, String operator, Object arg2) {
        assertEquals(expect,
                new ArithmeticNode().add(null, new LiteralNode(10)).add(operator, new LiteralNode(arg2)).evaluate(new Context(null)));
        assertEquals(Result.INVALID,
                new ArithmeticNode().add(null, new LiteralNode(10)).add(operator, new LiteralNode(Result.INVALID)).evaluate(new Context(null)));
    }
    @Test
    public void testThatInvalidPropagates() {
        verify(12.0, "+", 2);
        verify(8.0, "-", 2);
        verify(30.0, "*", 3);
        verify(5.0, "/", 2);
    }
}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author Simon Thoresen Hult
 */
public class ZCurveFunctionTestCase {

    @Test
    public void requireThatAccessorsWork() {
        ExpressionNode arg = new AttributeNode("foo");
        ZCurveFunctionNode node = new ZCurveFunctionNode(arg, ZCurveFunctionNode.Dimension.X);
        assertSame(arg, node.getArg());
        assertEquals(ZCurveFunctionNode.Dimension.X, node.getDimension());

        node = new ZCurveFunctionNode(arg, ZCurveFunctionNode.Dimension.Y);
        assertSame(arg, node.getArg());
        assertEquals(ZCurveFunctionNode.Dimension.Y, node.getDimension());
    }
}

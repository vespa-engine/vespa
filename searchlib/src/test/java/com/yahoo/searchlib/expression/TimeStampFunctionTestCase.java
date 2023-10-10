// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author Simon Thoresen Hult
 */
public class TimeStampFunctionTestCase {

    @Test
    public void requireThatAccessorsWork() {
        ExpressionNode arg = new AttributeNode("foo");
        for (TimeStampFunctionNode.TimePart part : TimeStampFunctionNode.TimePart.values()) {
            for (Boolean gmt : Arrays.asList(true, false)) {
                TimeStampFunctionNode node = new TimeStampFunctionNode(arg, part, gmt);
                assertSame(arg, node.getArg());
                assertEquals(part, node.getTimePart());
                assertEquals(gmt, node.isGmt());
                assertEquals(!gmt, node.isLocal());
            }
        }
    }
}

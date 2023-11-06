// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class ReferenceNodeTestCase {

    @Test
    public void requireThatAccessorsWork() {
        ReferenceNode node = new ReferenceNode("foo", Arrays.asList(new ReferenceNode("bar"), new ReferenceNode("baz")), "cox");
        assertEquals("foo", node.getName());
        List<ExpressionNode> args = node.getArguments().expressions();
        assertEquals(2, args.size());
        assertEquals(new ReferenceNode("bar"), args.get(0));
        assertEquals(new ReferenceNode("baz"), args.get(1));
        assertEquals("cox", node.getOutput());

        node = node.setArguments(Arrays.<ExpressionNode>asList(new ReferenceNode("bar@")));
        assertEquals(new ReferenceNode("bar@"), node.getArguments().expressions().get(0));

        node = node.setArguments(Arrays.<ExpressionNode>asList(new ReferenceNode("baz$")));
        assertEquals(new ReferenceNode("baz$"), node.getArguments().expressions().get(0));

        node = node.setOutput("cox'");
        assertEquals("cox'", node.getOutput());
    }
}

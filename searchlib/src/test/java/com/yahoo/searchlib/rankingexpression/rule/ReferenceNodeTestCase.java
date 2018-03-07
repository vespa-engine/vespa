// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen
 */
public class ReferenceNodeTestCase {

    @Test
    public void requireThatAccessorsWork() {
        ReferenceNode node = new ReferenceNode("foo", Arrays.asList(new NameNode("bar"), new NameNode("baz")), "cox");
        assertEquals("foo", node.getName());
        List<ExpressionNode> args = node.getArguments().expressions();
        assertEquals(2, args.size());
        assertEquals(new NameNode("bar"), args.get(0));
        assertEquals(new NameNode("baz"), args.get(1));
        assertEquals("cox", node.getOutput());

        node = node.setArguments(Arrays.<ExpressionNode>asList(new NameNode("bar'")));
        assertEquals(new NameNode("bar'"), node.getArguments().expressions().get(0));

        node = node.setArguments(Arrays.<ExpressionNode>asList(new NameNode("baz'")));
        assertEquals(new NameNode("baz'"), node.getArguments().expressions().get(0));

        node = node.setOutput("cox'");
        assertEquals("cox'", node.getOutput());
    }
}

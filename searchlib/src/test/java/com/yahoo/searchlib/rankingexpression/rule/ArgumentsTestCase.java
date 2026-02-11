// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ArgumentsTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Arguments args = new Arguments();
        assertTrue(args.expressions().isEmpty());

        args = new Arguments(Collections.<ExpressionNode>emptyList());
        assertTrue(args.expressions().isEmpty());

        var foo = new ReferenceNode("foo");
        var bar = new ReferenceNode("bar");
        args = new Arguments(List.of(foo, bar));
        assertEquals(2, args.expressions().size());
        assertSame(foo, args.expressions().get(0));
        assertSame(bar, args.expressions().get(1));
    }

    @Test
    public void requireThatHashCodeAndEqualsWork() {
        Arguments arg1 = new Arguments(List.of(new ReferenceNode("foo"), new ReferenceNode("bar")));
        Arguments arg2 = new Arguments(List.of(new ReferenceNode("foo"), new ReferenceNode("bar")));
        Arguments arg3 = new Arguments(List.of(new ReferenceNode("foo")));

        assertEquals(arg1.hashCode(), arg2.hashCode());
        assertEquals(arg1, arg2);
        assertNotEquals(arg2, arg3);
    }
}

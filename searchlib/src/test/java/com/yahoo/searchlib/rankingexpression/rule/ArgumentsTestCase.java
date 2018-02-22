// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class ArgumentsTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Arguments args = new Arguments(null);
        assertTrue(args.expressions().isEmpty());

        args = new Arguments(Collections.<ExpressionNode>emptyList());
        assertTrue(args.expressions().isEmpty());

        NameNode foo = new NameNode("foo");
        NameNode bar = new NameNode("bar");
        args = new Arguments(Arrays.asList(foo, bar));
        assertEquals(2, args.expressions().size());
        assertSame(foo, args.expressions().get(0));
        assertSame(bar, args.expressions().get(1));
    }

    @Test
    public void requireThatHashCodeAndEqualsWork() {
        Arguments arg1 = new Arguments(Arrays.asList(new NameNode("foo"), new NameNode("bar")));
        Arguments arg2 = new Arguments(Arrays.asList(new NameNode("foo"), new NameNode("bar")));
        Arguments arg3 = new Arguments(Arrays.asList(new NameNode("foo")));

        assertEquals(arg1.hashCode(), arg2.hashCode());
        assertTrue(arg1.equals(arg2));
        assertFalse(arg2.equals(arg3));
    }
}

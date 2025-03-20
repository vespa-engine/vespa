// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * @author Simon Thoresen Hult
 */
public class TypeContextTestCase {

    @Test
    public void requireThatVariablesCanBeSet() {
        TypeContext ctx = new TypeContext(new SimpleTestAdapter());
        DataType val = DataType.STRING;
        ctx.setVariableType("foo", val);
        assertSame(val, ctx.getVariableType("foo"));
    }

    @Test
    public void requireThatClearRemovesVariables() {
        TypeContext ctx = new TypeContext(new SimpleTestAdapter());
        ctx.setVariableType("foo", DataType.STRING);
        ctx.clear();
        assertNull(ctx.getVariableType("foo"));
    }
}

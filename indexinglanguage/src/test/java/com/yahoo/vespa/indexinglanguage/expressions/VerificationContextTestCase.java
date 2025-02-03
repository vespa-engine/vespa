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
public class VerificationContextTestCase {

    @Test
    public void requireThatVariablesCanBeSet() {
        VerificationContext ctx = new VerificationContext(new SimpleTestAdapter());
        DataType val = DataType.STRING;
        ctx.setVariable("foo", val);
        assertSame(val, ctx.getVariable("foo"));
    }

    @Test
    public void requireThatClearRemovesVariables() {
        VerificationContext ctx = new VerificationContext(new SimpleTestAdapter());
        ctx.setVariable("foo", DataType.STRING);
        ctx.clear();
        assertNull(ctx.getVariable("foo"));
    }
}

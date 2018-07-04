// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * @author Simon Thoresen Hult
 */
public class VerificationContextTestCase {

    @Test
    public void requireThatValueCanBeSet() {
        VerificationContext ctx = new VerificationContext();
        DataType val = DataType.STRING;
        ctx.setValue(val);
        assertSame(val, ctx.getValue());
    }

    @Test
    public void requireThatVariablesCanBeSet() {
        VerificationContext ctx = new VerificationContext();
        DataType val = DataType.STRING;
        ctx.setVariable("foo", val);
        assertSame(val, ctx.getVariable("foo"));
    }

    @Test
    public void requireThatClearRemovesValue() {
        VerificationContext ctx = new VerificationContext();
        ctx.setValue(DataType.STRING);
        ctx.clear();
        assertNull(ctx.getValue());
    }

    @Test
    public void requireThatClearRemovesVariables() {
        VerificationContext ctx = new VerificationContext();
        ctx.setVariable("foo", DataType.STRING);
        ctx.clear();
        assertNull(ctx.getVariable("foo"));
    }
}

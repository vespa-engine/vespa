// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class EchoTestCase {

    @Test
    public void requireThatAccessorsWork() {
        assertSame(System.out, new EchoExpression().getOutputStream());

        PrintStream out = new PrintStream(System.out);
        assertSame(out, new EchoExpression(out).getOutputStream());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        PrintStream out = new PrintStream(System.out);
        Expression exp = new EchoExpression(out);
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new EchoExpression()));
        assertFalse(exp.equals(new EchoExpression(new PrintStream(System.err))));
        assertEquals(exp, new EchoExpression(out));
        assertEquals(exp.hashCode(), new EchoExpression(out).hashCode());
    }

    @Test
    public void requireThatValueIsEchoed() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("69"));
        new EchoExpression(new PrintStream(out)).execute(ctx);

        assertEquals("69" + System.getProperty("line.separator"), out.toString());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new EchoExpression();
        assertVerify(DataType.INT, exp, DataType.INT);
        assertVerify(DataType.STRING, exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected any input, got null.");
    }
}

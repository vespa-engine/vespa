// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class GetVarTestCase {

    @Test
    public void requireThatAccessorsWorks() {
        GetVarExpression exp = new GetVarExpression("foo");
        assertEquals("foo", exp.getVariableName());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new GetVarExpression("foo");
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new GetVarExpression("bar")));
        assertEquals(exp, new GetVarExpression("foo"));
        assertEquals(exp.hashCode(), new GetVarExpression("foo").hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        VerificationContext ctx = new VerificationContext();
        ctx.setVariable("foo", DataType.STRING);

        assertEquals(DataType.STRING, new GetVarExpression("foo").verify(ctx));
        try {
            new GetVarExpression("bar").verify(ctx);
            fail();
        } catch (VerificationException e) {
            assertEquals("Variable 'bar' not found", e.getMessage());
        }
    }

    @Test
    public void requireThatSymbolIsRead() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setVariable("in", new IntegerFieldValue(69));
        new GetVarExpression("in").execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof IntegerFieldValue);
        assertEquals(69, ((IntegerFieldValue)val).getInteger());
    }

    @Test
    public void requireThatGetVarCanBeUsedToImplementSum() throws ParseException {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setOutputValue(null, "in", new StringFieldValue("0;1;2;3;4;5;6;7;8;9"));
        ScriptExpression.fromString("{ 0 | set_var tmp; " +
                                    "  input in | split ';' | for_each { to_int + get_var tmp | set_var tmp };" +
                                    "  get_var tmp | attribute out; }").execute(ctx);

        FieldValue val = ctx.getInputValue("out");
        assertTrue(val instanceof IntegerFieldValue);
        assertEquals(45, ((IntegerFieldValue)val).getInteger());
    }
}

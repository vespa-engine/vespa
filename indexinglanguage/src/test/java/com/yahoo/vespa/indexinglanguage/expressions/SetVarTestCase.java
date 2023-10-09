// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class SetVarTestCase {

    @Test
    public void requireThatAccessorsWork() {
        SetVarExpression exp = new SetVarExpression("foo");
        assertEquals("foo", exp.getVariableName());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new SetVarExpression("foo");
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new SetVarExpression("bar")));
        assertEquals(exp, new SetVarExpression("foo"));
        assertEquals(exp.hashCode(), new SetVarExpression("foo").hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new SetVarExpression("foo");
        assertVerify(DataType.INT, exp, DataType.INT);
        assertVerify(DataType.STRING, exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected any input, but no input is specified");

        try {
            new VerificationContext().setVariable("foo", DataType.INT).setValueType(DataType.STRING).execute(exp);
            fail();
        } catch (VerificationException e) {
            assertEquals("Attempting to assign conflicting types to variable 'foo', int vs string", e.getMessage());
        }
    }

    @Test
    public void requireThatSymbolIsWritten() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new IntegerFieldValue(69));
        new SetVarExpression("out").execute(ctx);

        FieldValue val = ctx.getVariable("out");
        assertTrue(val instanceof IntegerFieldValue);
        assertEquals(69, ((IntegerFieldValue)val).getInteger());
    }

    @Test
    public void requireThatVariableTypeCanNotChange() {
        VerificationContext ctx = new VerificationContext(new SimpleTestAdapter());
        ctx.setValueType(DataType.INT);
        new SetVarExpression("out").verify(ctx);

        try {
            ctx.setValueType(DataType.STRING);
            new SetVarExpression("out").verify(ctx);
            fail();
        } catch (VerificationException e) {
            assertTrue(e.getExpressionType().equals(SetVarExpression.class));
            assertEquals("Attempting to assign conflicting types to variable 'out', int vs string", e.getMessage());
        }
    }

    @Test
    public void requireThatAccessorWorks() {
        SetVarExpression exp = new SetVarExpression("69");
        assertEquals("69", exp.getVariableName());
    }
}

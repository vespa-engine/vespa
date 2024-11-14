// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ToDoubleTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ToDoubleExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ToDoubleExpression());
        assertEquals(exp.hashCode(), new ToDoubleExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ToDoubleExpression();
        assertVerify(DataType.INT, exp, DataType.DOUBLE);
        assertVerify(DataType.STRING, exp, DataType.DOUBLE);
        assertVerifyThrows("Invalid expression 'to_double': Expected any input, but no input is specified", null, exp);
    }

    @Test
    public void requireThatValueIsConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setCurrentValue(new StringFieldValue("6.9")).execute(new ToDoubleExpression());

        FieldValue val = ctx.getCurrentValue();
        assertTrue(val instanceof DoubleFieldValue);
        assertEquals(6.9, ((DoubleFieldValue)val).getDouble(), 1e-6);
    }
}

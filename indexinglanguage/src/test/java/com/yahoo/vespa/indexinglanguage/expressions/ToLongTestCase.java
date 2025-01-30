// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ToLongTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ToLongExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ToLongExpression());
        assertEquals(exp.hashCode(), new ToLongExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ToLongExpression();
        assertVerify(DataType.INT, exp, DataType.LONG);
        assertVerify(DataType.STRING, exp, DataType.LONG);
        assertVerifyThrows("Invalid expression 'to_long': Expected input, but no input is provided", null, exp);
    }

    @Test
    public void requireThatValueIsConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setCurrentValue(new StringFieldValue("69")).execute(new ToLongExpression());

        FieldValue val = ctx.getCurrentValue();
        assertTrue(val instanceof LongFieldValue);
        assertEquals(69L, ((LongFieldValue)val).getLong());
    }
}

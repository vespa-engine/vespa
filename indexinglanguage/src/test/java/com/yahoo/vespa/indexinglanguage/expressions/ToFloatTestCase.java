// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ToFloatTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ToFloatExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ToFloatExpression());
        assertEquals(exp.hashCode(), new ToFloatExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ToFloatExpression();
        assertVerify(DataType.INT, exp, DataType.FLOAT);
        assertVerify(DataType.STRING, exp, DataType.FLOAT);
        assertVerifyThrows(null, exp, "Expected any input, but no input is specified");
    }

    @Test
    public void requireThatValueIsConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("6.9f")).execute(new ToFloatExpression());

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof FloatFieldValue);
        assertEquals(6.9f, ((FloatFieldValue)val).getFloat(), 1e-6);
    }
}

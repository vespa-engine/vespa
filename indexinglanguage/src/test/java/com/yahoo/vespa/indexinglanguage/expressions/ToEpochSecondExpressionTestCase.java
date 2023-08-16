// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class ToEpochSecondExpressionTestCase {
    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ToEpochSecondExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ToEpochSecondExpression());
        assertEquals(exp.hashCode(), new ToEpochSecondExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ToEpochSecondExpression();
        assertVerify(DataType.STRING, exp, DataType.LONG);
        assertVerifyThrows(DataType.INT, exp, "Expected string input, got int.");
        assertVerifyThrows(null, exp, "Expected string input, got null.");
    }

    @Test
    public void requireThatValueIsConvertedWithMs() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("2023-12-24T17:00:43.000Z")).execute(new ToEpochSecondExpression());
        FieldValue val = ctx.getValue();
        assertTrue(val instanceof LongFieldValue);
        assertEquals(1703437243L, ((LongFieldValue)val).getLong());
    }

    @Test
    public void requireThatValueIsConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("2023-12-24T17:00:43Z")).execute(new ToEpochSecondExpression());
        FieldValue val = ctx.getValue();
        assertTrue(val instanceof LongFieldValue);
        assertEquals(1703437243L, ((LongFieldValue)val).getLong());
    }
}

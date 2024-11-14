// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ToBoolTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ToBoolExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ToBoolExpression());
        assertEquals(exp.hashCode(), new ToBoolExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ToBoolExpression();
        assertVerify(DataType.INT, exp, DataType.BOOL);
        assertVerify(DataType.STRING, exp, DataType.BOOL);
        assertVerifyThrows("Invalid expression 'to_bool': Expected any input, but no input is specified", null, exp);
    }

    @Test
    public void requireThatNonEmptyStringBecomesTrue() {
        ExecutionContext context = new ExecutionContext(new SimpleTestAdapter());
        context.setCurrentValue(new StringFieldValue("false")).execute(new ToBoolExpression());
        FieldValue value = context.getCurrentValue();
        assertTrue(value instanceof BoolFieldValue);
        assertTrue(((BoolFieldValue)value).getBoolean());
    }

    @Test
    public void requireThatEmptyStringBecomesFalse() {
        ExecutionContext context = new ExecutionContext(new SimpleTestAdapter());
        context.setCurrentValue(new StringFieldValue("")).execute(new ToBoolExpression());
        FieldValue value = context.getCurrentValue();
        assertTrue(value instanceof BoolFieldValue);
        assertFalse(((BoolFieldValue)value).getBoolean());
    }

    @Test
    public void requireThatNonZeroBecomesTrue() {
        ExecutionContext context = new ExecutionContext(new SimpleTestAdapter());
        context.setCurrentValue(new IntegerFieldValue(37)).execute(new ToBoolExpression());
        FieldValue value = context.getCurrentValue();
        assertTrue(value instanceof BoolFieldValue);
        assertTrue(((BoolFieldValue)value).getBoolean());
    }

    @Test
    public void requireThatZeroBecomesFalse() {
        ExecutionContext context = new ExecutionContext(new SimpleTestAdapter());
        context.setCurrentValue(new IntegerFieldValue(0)).execute(new ToBoolExpression());
        FieldValue value = context.getCurrentValue();
        assertTrue(value instanceof BoolFieldValue);
        assertFalse(((BoolFieldValue)value).getBoolean());
    }

}

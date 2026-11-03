// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class AbsTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new AbsExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new AbsExpression());
        assertEquals(exp.hashCode(), new AbsExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        assertVerify(DataType.INT, new AbsExpression(), DataType.INT);
        assertVerify(DataType.LONG, new AbsExpression(), DataType.LONG);
        assertVerify(DataType.FLOAT, new AbsExpression(), DataType.FLOAT);
        assertVerify(DataType.DOUBLE, new AbsExpression(), DataType.DOUBLE);
        assertVerify(DataType.BYTE, new AbsExpression(), DataType.BYTE);
        assertVerifyThrows("Invalid expression 'abs': Expected numeric input, but no input is provided",
                           null, new AbsExpression());
        assertVerifyThrows("Invalid expression 'abs': Expected numeric input, got string",
                           DataType.STRING, new AbsExpression());
    }

    @Test
    public void requireThatNonNumericOutputIsRejected() {
        var context = new TypeContext(new SimpleTestAdapter());
        try {
            new AbsExpression().setOutputType(DataType.STRING, context);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals("Invalid expression 'abs': Produces a numeric value, but type string is required", e.getMessage());
        }
    }

    @Test
    public void requireThatAbsoluteValueIsComputed() {
        assertAbs(new IntegerFieldValue(69), new IntegerFieldValue(-69));
        assertAbs(new IntegerFieldValue(69), new IntegerFieldValue(69));
        assertAbs(new IntegerFieldValue(0), new IntegerFieldValue(0));
        assertAbs(new LongFieldValue(69L), new LongFieldValue(-69L));
        assertAbs(new FloatFieldValue(6.9f), new FloatFieldValue(-6.9f));
        assertAbs(new DoubleFieldValue(6.9), new DoubleFieldValue(-6.9));
        assertAbs(new ByteFieldValue((byte)69), new ByteFieldValue((byte)-69));
    }

    @Test
    public void requireThatMostNegativeValueIsUnchanged() {
        assertAbs(new IntegerFieldValue(Integer.MIN_VALUE), new IntegerFieldValue(Integer.MIN_VALUE));
        assertAbs(new LongFieldValue(Long.MIN_VALUE), new LongFieldValue(Long.MIN_VALUE));
        assertAbs(new ByteFieldValue(Byte.MIN_VALUE), new ByteFieldValue(Byte.MIN_VALUE));
    }

    private static void assertAbs(FieldValue expected, FieldValue input) {
        ExecutionContext context = new ExecutionContext(new SimpleTestAdapter());
        context.setCurrentValue(input).execute(new AbsExpression());
        assertEquals(expected, context.getCurrentValue());
    }

}

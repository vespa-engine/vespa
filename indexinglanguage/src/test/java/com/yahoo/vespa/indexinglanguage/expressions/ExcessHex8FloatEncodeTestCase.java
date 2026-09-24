// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author arnej
 */
public class ExcessHex8FloatEncodeTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ExcessHex8FloatEncodeExpression();
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new ExcessHex8EncodeExpression()));
        assertEquals(exp, new ExcessHex8FloatEncodeExpression());
        assertEquals(exp.hashCode(), new ExcessHex8FloatEncodeExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeParsed() throws ParseException {
        assertEquals(new ExcessHex8FloatEncodeExpression(), Expression.fromString("exhex8floatencode"));
        assertEquals("exhex8floatencode", Expression.fromString("exhex8floatencode").toString());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ExcessHex8FloatEncodeExpression();
        assertVerify(DataType.FLOAT, exp, DataType.STRING);
        assertVerifyThrows("Invalid expression 'exhex8floatencode': Expected float input, but no input is provided", null, exp);
        assertVerifyThrows("Invalid expression 'exhex8floatencode': Expected float input, got string", DataType.STRING, exp);
        assertVerifyThrows("Invalid expression 'exhex8floatencode': Expected float input, got double", DataType.DOUBLE, exp);
    }

    @Test
    public void requireThatInputIsEncoded() {
        float[] values = new float[] { Float.NEGATIVE_INFINITY, -1.0f, -0.0f, 0.0f, 1.0f, Float.POSITIVE_INFINITY };
        String[] strings = new String[] { "007fffff", "407fffff", "7fffffff", "80000000", "bf800000", "ff800000" };
        for (int i = 0; i < values.length; i++) {
            ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
            ctx.setCurrentValue(new FloatFieldValue(values[i]));
            new ExcessHex8FloatEncodeExpression().execute(ctx);

            FieldValue val = ctx.getCurrentValue();
            assertTrue(val instanceof StringFieldValue);
            assertEquals(strings[i], ((StringFieldValue)val).getString());
        }
    }

}

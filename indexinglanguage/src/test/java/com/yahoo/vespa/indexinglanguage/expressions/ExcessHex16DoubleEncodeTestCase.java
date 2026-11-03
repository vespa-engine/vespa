// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
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
public class ExcessHex16DoubleEncodeTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ExcessHex16DoubleEncodeExpression();
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new ExcessHex16EncodeExpression()));
        assertEquals(exp, new ExcessHex16DoubleEncodeExpression());
        assertEquals(exp.hashCode(), new ExcessHex16DoubleEncodeExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeParsed() throws ParseException {
        assertEquals(new ExcessHex16DoubleEncodeExpression(), Expression.fromString("exhex16doubleencode"));
        assertEquals("exhex16doubleencode", Expression.fromString("exhex16doubleencode").toString());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ExcessHex16DoubleEncodeExpression();
        assertVerify(DataType.DOUBLE, exp, DataType.STRING);
        assertVerifyThrows("Invalid expression 'exhex16doubleencode': Expected double input, but no input is provided", null, exp);
        assertVerifyThrows("Invalid expression 'exhex16doubleencode': Expected double input, got string", DataType.STRING, exp);
        assertVerifyThrows("Invalid expression 'exhex16doubleencode': Expected double input, got float", DataType.FLOAT, exp);
    }

    @Test
    public void requireThatInputIsEncoded() {
        double[] values = new double[] { Double.NEGATIVE_INFINITY, -1.0, -0.0, 0.0, 1.0, Double.POSITIVE_INFINITY };
        String[] strings = new String[] { "000fffffffffffff", "400fffffffffffff", "7fffffffffffffff",
                                          "8000000000000000", "bff0000000000000", "fff0000000000000" };
        for (int i = 0; i < values.length; i++) {
            ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
            ctx.setCurrentValue(new DoubleFieldValue(values[i]));
            new ExcessHex16DoubleEncodeExpression().execute(ctx);

            FieldValue val = ctx.getCurrentValue();
            assertTrue(val instanceof StringFieldValue);
            assertEquals(strings[i], ((StringFieldValue)val).getString());
        }
    }

}

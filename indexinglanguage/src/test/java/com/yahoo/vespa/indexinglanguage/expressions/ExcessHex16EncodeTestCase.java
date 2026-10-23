// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
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
 * @author johsol
 */
public class ExcessHex16EncodeTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ExcessHex16EncodeExpression();
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new ExcessHex8EncodeExpression()));
        assertEquals(exp, new ExcessHex16EncodeExpression());
        assertEquals(exp.hashCode(), new ExcessHex16EncodeExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeParsed() throws ParseException {
        assertEquals(new ExcessHex16EncodeExpression(), Expression.fromString("exhex16encode"));
        assertEquals("exhex16encode", Expression.fromString("exhex16encode").toString());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ExcessHex16EncodeExpression();
        assertVerify(DataType.LONG, exp, DataType.STRING);
        assertVerifyThrows("Invalid expression 'exhex16encode': Expected long input, but no input is provided", null, exp);
        assertVerifyThrows("Invalid expression 'exhex16encode': Expected long input, got string", DataType.STRING, exp);
        assertVerifyThrows("Invalid expression 'exhex16encode': Expected long input, got int", DataType.INT, exp);
    }

    @Test
    public void requireThatInputIsEncoded() {
        long[] values = new long[] { Long.MIN_VALUE, Integer.MIN_VALUE, -1L, 0L, 1L, Integer.MAX_VALUE, 1L << 32, Long.MAX_VALUE };
        String[] strings = new String[] { "0000000000000000", "7fffffff80000000", "7fffffffffffffff", "8000000000000000",
                                          "8000000000000001", "800000007fffffff", "8000000100000000", "ffffffffffffffff" };
        for (int i = 0; i < values.length; i++) {
            ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
            ctx.setCurrentValue(new LongFieldValue(values[i]));
            new ExcessHex16EncodeExpression().execute(ctx);

            FieldValue val = ctx.getCurrentValue();
            assertTrue(val instanceof StringFieldValue);
            assertEquals(strings[i], ((StringFieldValue)val).getString());
        }
    }

}

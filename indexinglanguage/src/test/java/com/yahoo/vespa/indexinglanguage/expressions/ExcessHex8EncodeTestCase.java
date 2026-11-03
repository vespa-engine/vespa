// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
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
 * @author boeker
 */
public class ExcessHex8EncodeTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ExcessHex8EncodeExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ExcessHex8EncodeExpression());
        assertEquals(exp.hashCode(), new ExcessHex8EncodeExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ExcessHex8EncodeExpression();
        assertVerify(DataType.INT, exp, DataType.STRING);
        assertVerifyThrows("Invalid expression 'exhex8encode': Expected int input, but no input is provided", null, exp);
        assertVerifyThrows("Invalid expression 'exhex8encode': Expected int input, got string", DataType.STRING, exp);
    }

    @Test
    public void requireThatInputIsEncoded() {
        int[] values = new int[] { Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE };
        String[] strings = new String[] { "00000000", "7fffffff", "80000000", "80000001", "ffffffff" };
        for (int i = 0; i < values.length; i++) {
            ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
            ctx.setCurrentValue(new IntegerFieldValue(values[i]));
            new ExcessHex8EncodeExpression().execute(ctx);

            FieldValue val = ctx.getCurrentValue();
            assertTrue(val instanceof StringFieldValue);
            assertEquals(strings[i], ((StringFieldValue)val).getString());
        }
    }
}

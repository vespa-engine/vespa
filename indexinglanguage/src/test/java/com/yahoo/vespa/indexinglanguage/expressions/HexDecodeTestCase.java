// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class HexDecodeTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new HexDecodeExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new HexDecodeExpression());
        assertEquals(exp.hashCode(), new HexDecodeExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new HexDecodeExpression();
        assertVerify(DataType.STRING, exp, DataType.LONG);
        assertVerifyThrows(null, exp, "Expected string input, got null.");
        assertVerifyThrows(DataType.LONG, exp, "Expected string input, got long.");
    }

    @Test
    public void requireInputIsDecoded() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("1d28c2cd"));
        new HexDecodeExpression().execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof LongFieldValue);
        assertEquals(489210573L, ((LongFieldValue)val).getLong());
    }

    @Test
    public void requireThatLargeInputIsDecoded() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("ff7a3c87fd74abff"));
        new HexDecodeExpression().execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof LongFieldValue);
        assertEquals(-37651092108694529L, ((LongFieldValue)val).getLong());
    }

    @Test
    public void requireThatEmptyStringDecodesToLongMinValue() {
        assertEquals(new LongFieldValue(Long.MIN_VALUE),
                     new HexDecodeExpression().execute(new StringFieldValue("")));
    }

    @Test
    public void requireThatInputDoesNotExceedMaxLength() {
        try {
            new HexDecodeExpression().execute(new StringFieldValue("1ffffffffffffffff"));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Hex value '1ffffffffffffffff' is out of range.", e.getMessage());
        }
    }

    @Test
    public void requireThatIllegalInputThrows() {
        try {
            new HexDecodeExpression().execute(new StringFieldValue("???"));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Illegal hex value '???'.", e.getMessage());
        }
    }
}

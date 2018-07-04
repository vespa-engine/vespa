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
public class Base64DecodeTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new Base64DecodeExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new Base64DecodeExpression());
        assertEquals(exp.hashCode(), new Base64DecodeExpression().hashCode());
    }

    @Test
    public void requireThatInputIsDecoded() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("zcIoHQ"));
        new Base64DecodeExpression().execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof LongFieldValue);
        assertEquals(489210573L, ((LongFieldValue)val).getLong());
    }

    @Test
    public void requireThatEmptyStringDecodesToLongMinValue() {
        assertEquals(new LongFieldValue(Long.MIN_VALUE),
                     new Base64DecodeExpression().execute(new StringFieldValue("")));
    }

    @Test
    public void requireThatInputDoesNotExceedMaxLength() {
        try {
            new Base64DecodeExpression().execute(new StringFieldValue("abcdefghijlkm"));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Base64 value 'abcdefghijlkm' is out of range.", e.getMessage());
        }
    }

    @Test
    public void requireThatIllegalInputThrows() {
        try {
            new Base64DecodeExpression().execute(new StringFieldValue("???"));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Illegal base64 value '???'.", e.getMessage());
        }
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new Base64DecodeExpression();
        assertVerify(DataType.STRING, exp, DataType.LONG);
        assertVerifyThrows(null, exp, "Expected string input, got null.");
        assertVerifyThrows(DataType.LONG, exp, "Expected string input, got long.");
    }
}

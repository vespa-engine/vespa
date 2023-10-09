// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ToByteTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ToByteExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ToByteExpression());
        assertEquals(exp.hashCode(), new ToByteExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ToByteExpression();
        assertVerify(DataType.INT, exp, DataType.BYTE);
        assertVerify(DataType.STRING, exp, DataType.BYTE);
        assertVerifyThrows(null, exp, "Expected any input, but no input is specified");
    }

    @Test
    public void requireThatValueIsConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("69")).execute(new ToByteExpression());

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof ByteFieldValue);
        assertEquals(69, ((ByteFieldValue)val).getByte());
    }
}

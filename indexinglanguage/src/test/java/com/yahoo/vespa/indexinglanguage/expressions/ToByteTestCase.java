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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertVerify(DataType.INT, new ToByteExpression(), DataType.BYTE);
        assertVerify(DataType.STRING, new ToByteExpression(), DataType.BYTE);
    }

    @Test
    public void requireThatValueIsConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setCurrentValue(new StringFieldValue("69")).execute(new ToByteExpression());

        FieldValue val = ctx.getCurrentValue();
        assertTrue(val instanceof ByteFieldValue);
        assertEquals(69, ((ByteFieldValue)val).getByte());
    }
}

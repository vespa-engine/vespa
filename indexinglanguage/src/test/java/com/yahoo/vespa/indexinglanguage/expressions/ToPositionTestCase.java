// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ToPositionTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ToPositionExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ToPositionExpression());
        assertEquals(exp.hashCode(), new ToPositionExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ToPositionExpression();
        assertVerify(DataType.STRING, exp, PositionDataType.INSTANCE);
        assertVerifyThrows(null, exp, "Expected string input, got null.");
        assertVerifyThrows(DataType.INT, exp, "Expected string input, got int.");
    }

    @Test
    public void requireThatPositionIsParsed() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("6;9")).execute(new ToPositionExpression());

        FieldValue out = ctx.getValue();
        assertTrue(out instanceof StructuredFieldValue);
        assertEquals(PositionDataType.INSTANCE, out.getDataType());

        FieldValue val = ((StructuredFieldValue)out).getFieldValue("x");
        assertTrue(val instanceof IntegerFieldValue);
        assertEquals(6, ((IntegerFieldValue)val).getInteger());

        val = ((StructuredFieldValue)out).getFieldValue("y");
        assertTrue(val instanceof IntegerFieldValue);
        assertEquals(9, ((IntegerFieldValue)val).getInteger());
    }
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ToIntegerTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ToIntegerExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ToIntegerExpression());
        assertEquals(exp.hashCode(), new ToIntegerExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ToIntegerExpression();
        assertVerify(DataType.INT, exp, DataType.INT);
        assertVerify(DataType.STRING, exp, DataType.INT);
        assertVerifyThrows(null, exp, "Expected any input, got null.");
    }

    @Test
    public void requireThatValueIsConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("69")).execute(new ToIntegerExpression());

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof IntegerFieldValue);
        assertEquals(69, ((IntegerFieldValue)val).getInteger());
    }
}

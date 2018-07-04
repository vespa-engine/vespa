// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class RandomTestCase {

    @Test
    public void requireThatAccessorsWork() {
        RandomExpression exp = new RandomExpression(69);
        assertEquals(Integer.valueOf(69), exp.getMaxValue());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new RandomExpression(69);
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new RandomExpression(96)));
        assertEquals(exp, new RandomExpression(69));
        assertEquals(exp.hashCode(), new RandomExpression(69).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new RandomExpression();
        assertVerify(null, exp, DataType.INT);
        assertVerify(DataType.INT, exp, DataType.INT);
        assertVerify(DataType.STRING, exp, DataType.INT);
    }

    @Test
    public void requireThatRandomValueIsSet() {
        for (int i = 0; i < 666; ++i) {
            ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
            new RandomExpression(69).execute(ctx);

            FieldValue val = ctx.getValue();
            assertTrue(val instanceof IntegerFieldValue);
            assertTrue(((IntegerFieldValue)val).getInteger() < 69);
        }
    }

    @Test
    public void requireThatInputValueIsParsedAsMaxIfNoneIsConfigured() {
        for (int i = 0; i < 666; ++i) {
            ExecutionContext ctx = new ExecutionContext().setValue(new IntegerFieldValue(69));
            new RandomExpression().execute(ctx);

            FieldValue val = ctx.getValue();
            assertTrue(val instanceof IntegerFieldValue);
            assertTrue(((IntegerFieldValue)val).getInteger() < 69);
        }
    }

    @Test
    public void requireThatIllegalInputThrowsNumberFormatException() {
        try {
            new RandomExpression().execute(new ExecutionContext());
            fail();
        } catch (NumberFormatException e) {

        }
        try {
            new RandomExpression().execute(new ExecutionContext().setValue(new StringFieldValue("foo")));
            fail();
        } catch (NumberFormatException e) {

        }
    }

    @Test
    public void requireThatDefaultMaxIsNull() {
        assertNull(new RandomExpression().getMaxValue());
    }
}

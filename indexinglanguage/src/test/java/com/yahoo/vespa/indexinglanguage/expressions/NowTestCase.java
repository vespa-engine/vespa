// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class NowTestCase {

    @Test
    public void requireThatAccessorsWork() {
        MyTimer timer = new MyTimer();
        NowExpression exp = new NowExpression(timer);
        assertSame(timer, exp.getTimer());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        MyTimer timer = new MyTimer();
        NowExpression exp = new NowExpression(timer);
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new NowExpression(new MyTimer())));
        assertEquals(exp, new NowExpression(timer));
        assertEquals(exp.hashCode(), new NowExpression(timer).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new NowExpression();
        assertVerify(null, exp, DataType.LONG);
        assertVerify(DataType.INT, exp, DataType.LONG);
        assertVerify(DataType.STRING, exp, DataType.LONG);
    }

    @Test
    public void requireThatCurrentTimeIsSet() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        new NowExpression(new MyTimer()).execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof LongFieldValue);
        assertEquals(69L, ((LongFieldValue)val).getLong());
    }

    private class MyTimer implements NowExpression.Timer {

        @Override
        public long currentTimeSeconds() {
            return 69L;
        }
    }
}

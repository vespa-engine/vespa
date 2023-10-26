// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ClearStateTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ClearStateExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ClearStateExpression());
        assertEquals(exp.hashCode(), new ClearStateExpression().hashCode());
    }

    @Test
    public void requireThatExecutionContextIsCleared() {
        MyExecution ctx = new MyExecution();
        ctx.execute(new ClearStateExpression());
        assertTrue(ctx.cleared);
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ClearStateExpression();
        assertVerify(null, exp, null);
        assertVerify(DataType.INT, exp, null);
        assertVerify(DataType.STRING, exp, null);
    }

    @Test
    public void requireThatVerificationContextIsCleared() {
        MyVerification ctx = new MyVerification();
        ctx.execute(new ClearStateExpression());
        assertTrue(ctx.cleared);
    }

    private static class MyExecution extends ExecutionContext {

        boolean cleared = false;

        @Override
        public ExecutionContext clear() {
            cleared = true;
            return this;
        }
    }

    private static class MyVerification extends VerificationContext {

        boolean cleared = false;

        @Override
        public VerificationContext clear() {
            cleared = true;
            return this;
        }
    }
}

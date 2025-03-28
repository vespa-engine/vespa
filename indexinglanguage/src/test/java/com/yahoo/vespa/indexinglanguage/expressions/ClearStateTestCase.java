// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertVerify(DataType.INT, new ClearStateExpression(), DataType.INT);
        assertVerify(DataType.STRING, new ClearStateExpression(), DataType.STRING);
    }

    private static class MyExecution extends ExecutionContext {

        boolean cleared = false;

        @Override
        public ExecutionContext clear() {
            cleared = true;
            return this;
        }
    }

    private static class MyVerification extends TypeContext {

        boolean cleared = false;

        MyVerification() {
            super(new SimpleTestAdapter());
        }

        @Override
        public TypeContext clear() {
            cleared = true;
            return this;
        }
    }
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.predicate.Predicate;
import org.junit.Test;
import org.mockito.Mockito;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyCtx;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyCtxThrows;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class OptimizePredicateTestCase {

    @Test
    public void requireThatOptimizerIsCalledWithCloneOfInput() {
        final Predicate predicateA = Mockito.mock(Predicate.class);
        final Predicate predicateB = Mockito.mock(Predicate.class);
        final PredicateFieldValue input = new PredicateFieldValue(predicateA);
        ExecutionContext ctx = new ExecutionContext()
                .setValue(input)
                .setVariable("arity", new IntegerFieldValue(10));
        FieldValue output = new OptimizePredicateExpression(
                (predicate, options) -> {
                    assertNotSame(predicateA, predicate);
                    return predicateB;
                }
        ).execute(ctx);
        assertNotSame(output, input);
        assertTrue(output instanceof PredicateFieldValue);
        assertSame(predicateB, ((PredicateFieldValue)output).getPredicate());
    }

    @Test
    public void requireThatPredicateOptionsAreSet() {
        final Predicate predicate = Mockito.mock(Predicate.class);
        final PredicateFieldValue input = new PredicateFieldValue(predicate);
        ExecutionContext ctx = new ExecutionContext()
                .setValue(input)
                .setVariable("arity", new IntegerFieldValue(10));
        new OptimizePredicateExpression((predicate1, options) -> {
            assertEquals(10, options.getArity());
            assertEquals(0x8000000000000000L, options.getLowerBound());
            assertEquals(0x7fffffffffffffffL, options.getUpperBound());
            return predicate1;
        }).execute(ctx);
        ctx.setVariable("upper_bound", new LongFieldValue(1000));
        ctx.setVariable("lower_bound", new LongFieldValue(0));
        new OptimizePredicateExpression(
                (value, options) -> {
                    assertEquals(10, options.getArity());
                    assertEquals(0, options.getLowerBound());
                    assertEquals(1000, options.getUpperBound());
                    return value;
                }
        ).execute(ctx);
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new OptimizePredicateExpression();
        assertTrue(exp.equals(exp));
        assertFalse(exp.equals(new Object()));
        assertTrue(exp.equals(new OptimizePredicateExpression()));
        assertEquals(exp.hashCode(), new OptimizePredicateExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new OptimizePredicateExpression();
        assertVerifyThrows(null, exp, "Expected predicate input, got null.");
        assertVerifyThrows(DataType.INT, exp, "Expected predicate input, got int.");
        assertVerifyThrows(DataType.PREDICATE, exp, "Variable 'arity' must be set.");

        VerificationContext context = new VerificationContext().setValue(DataType.PREDICATE);
        context.setVariable("arity", DataType.STRING);
        assertVerifyCtxThrows(context, exp, "Variable 'arity' must have type int.");
        context.setVariable("arity", DataType.INT);
        assertVerifyCtx(context, exp, DataType.PREDICATE);
        context.setVariable("lower_bound", DataType.INT);
        assertVerifyCtxThrows(context, exp, "Variable 'lower_bound' must have type long.");
        context.setVariable("lower_bound", DataType.LONG);
        assertVerifyCtx(context, exp, DataType.PREDICATE);
        context.setVariable("upper_bound", DataType.INT);
        assertVerifyCtxThrows(context, exp,  "Variable 'upper_bound' must have type long.");
        context.setVariable("upper_bound", DataType.LONG);
        assertVerifyCtx(context, exp, DataType.PREDICATE);
    }
}

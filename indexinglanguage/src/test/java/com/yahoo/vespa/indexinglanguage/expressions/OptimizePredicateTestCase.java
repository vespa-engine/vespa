// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;
import org.mockito.Mockito;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyCtx;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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
                .setCurrentValue(input)
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
                .setCurrentValue(input)
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
    public void requireThatRangesOutsideBoundsAreRejectedBeforeOptimization() {
        for (String range : new String[] {"[100001..100002]", "[99999..100002]", "[-12..-11]", "[-11..0]",
                                          "[100001..]", "[..-11]"}) {
            PredicateFieldValue input = new PredicateFieldValue("approval_date in " + range);
            var context = boundedContext(input);
            var expression = new OptimizePredicateExpression((predicate, options) -> {
                throw new AssertionError("Out-of-bounds predicates must be rejected before optimization");
            });
            var exception = assertThrows(InvalidInputException.class,
                                         () -> expression.execute(context));
            assertEquals("Predicate attribute 'approval_date' has range " + range +
                         " outside configured bounds [-10..100000].", exception.getMessage());
            assertEquals(new PredicateFieldValue("approval_date in " + range), input);
        }
    }

    @Test
    public void requireThatBoundsAreValidatedInNestedPredicates() {
        for (String predicate : new String[] {
                "category in [a] and approval_date in [100001..100002]",
                "category in [a] or approval_date in [99999..100002]",
                "not (category in [a] or (price in [0..10] and approval_date in [-11..0]))"}) {
            assertThrows(InvalidInputException.class,
                         () -> new OptimizePredicateExpression().execute(boundedContext(new PredicateFieldValue(predicate))));
        }
    }

    @Test
    public void requireThatInclusiveBoundsAndOpenRangesRemainValid() {
        for (String predicate : new String[] {"approval_date in [-10..100000]", "approval_date in [-10..-10]",
                                              "approval_date in [100000..100000]", "approval_date in [10..]",
                                              "approval_date in [..10]", "approval_date in [100000..]",
                                              "approval_date in [..-10]", "approval_date in [..]",
                                              "not (approval_date in [0..10])", "category in [a]", "true", "false"}) {
            var input = new PredicateFieldValue(predicate);
            var output = new OptimizePredicateExpression().execute(boundedContext(input));
            assertTrue(output instanceof PredicateFieldValue);
        }
    }

    @Test
    public void requireThatDefaultBoundsAcceptTheFullLongRange() {
        var input = new PredicateFieldValue("value in [-9223372036854775808..9223372036854775807]");
        var context = new ExecutionContext().setCurrentValue(input)
                                            .setVariable("arity", new IntegerFieldValue(2));
        assertTrue(new OptimizePredicateExpression().execute(context) instanceof PredicateFieldValue);
    }

    private static ExecutionContext boundedContext(PredicateFieldValue input) {
        return new ExecutionContext().setCurrentValue(input)
                                     .setVariable("arity", new IntegerFieldValue(2))
                                     .setVariable("lower_bound", new LongFieldValue(-10))
                                     .setVariable("upper_bound", new LongFieldValue(100000));
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
        String prefix = "Invalid expression 'optimize_predicate': ";
        assertVerifyThrows(prefix + "Expected predicate input, but no input is provided", null, exp);
        assertVerifyThrows(prefix + "Expected predicate input, got int", DataType.INT, exp);
        assertVerifyThrows(prefix + "Variable 'arity' must be set", DataType.PREDICATE, exp);

        TypeContext context = new TypeContext(new SimpleTestAdapter());
        context.setVariableType("arity", DataType.STRING);
        ExpressionAssert.assertVerifyThrows(prefix + "Variable 'arity' must have type int", exp, DataType.PREDICATE, context);
        context.setVariableType("arity", DataType.INT);
        assertVerifyCtx(exp, context);
        context.setVariableType("lower_bound", DataType.INT);
        ExpressionAssert.assertVerifyThrows(prefix + "Variable 'lower_bound' must have type long", exp, DataType.PREDICATE, context);
        context.setVariableType("lower_bound", DataType.LONG);
        assertVerifyCtx(exp, context);
        context.setVariableType("upper_bound", DataType.INT);
        ExpressionAssert.assertVerifyThrows(prefix + "Variable 'upper_bound' must have type long", exp, DataType.PREDICATE, context);
        context.setVariableType("upper_bound", DataType.LONG);
        assertVerifyCtx(exp, context);
    }

}

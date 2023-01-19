// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class MathResolverTestCase {

    // --------------------------------------------------------------------------------
    //
    // Tests
    //
    // --------------------------------------------------------------------------------

    @Test
    public void requireThatOperatorsWork() {
        assertEquals(3, evaluate(1, ArithmeticExpression.Operator.ADD, 2));
        assertEquals(1, evaluate(3, ArithmeticExpression.Operator.SUB, 2));
        assertEquals(2, evaluate(4, ArithmeticExpression.Operator.DIV, 2));
        assertEquals(1, evaluate(3, ArithmeticExpression.Operator.MOD, 2));
        assertEquals(4, evaluate(2, ArithmeticExpression.Operator.MUL, 2));
    }

    @Test
    public void requireThatOperatorPrecedenceIsCorrect() {
        assertEquals(3, evaluate(1, ArithmeticExpression.Operator.ADD, 1, ArithmeticExpression.Operator.ADD, 1));
        assertEquals(1, evaluate(1, ArithmeticExpression.Operator.ADD, 1, ArithmeticExpression.Operator.SUB, 1));
        assertEquals(4, evaluate(2, ArithmeticExpression.Operator.ADD, 4, ArithmeticExpression.Operator.DIV, 2));
        assertEquals(2, evaluate(1, ArithmeticExpression.Operator.ADD, 3, ArithmeticExpression.Operator.MOD, 2));
        assertEquals(3, evaluate(1, ArithmeticExpression.Operator.ADD, 1, ArithmeticExpression.Operator.MUL, 2));

        assertEquals(1, evaluate(1, ArithmeticExpression.Operator.SUB, 1, ArithmeticExpression.Operator.ADD, 1));
        assertEquals(-1, evaluate(1, ArithmeticExpression.Operator.SUB, 1, ArithmeticExpression.Operator.SUB, 1));
        assertEquals(-1, evaluate(1, ArithmeticExpression.Operator.SUB, 4, ArithmeticExpression.Operator.DIV, 2));
        assertEquals(1, evaluate(2, ArithmeticExpression.Operator.SUB, 3, ArithmeticExpression.Operator.MOD, 2));
        assertEquals(-1, evaluate(1, ArithmeticExpression.Operator.SUB, 1, ArithmeticExpression.Operator.MUL, 2));

        assertEquals(3, evaluate(4, ArithmeticExpression.Operator.DIV, 2, ArithmeticExpression.Operator.ADD, 1));
        assertEquals(1, evaluate(4, ArithmeticExpression.Operator.DIV, 2, ArithmeticExpression.Operator.SUB, 1));
        assertEquals(2, evaluate(4, ArithmeticExpression.Operator.DIV, 2, ArithmeticExpression.Operator.DIV, 1));
        assertEquals(2, evaluate(4, ArithmeticExpression.Operator.DIV, 2, ArithmeticExpression.Operator.MOD, 3));
        assertEquals(2, evaluate(4, ArithmeticExpression.Operator.DIV, 2, ArithmeticExpression.Operator.MUL, 1));

        assertEquals(2, evaluate(3, ArithmeticExpression.Operator.MOD, 2, ArithmeticExpression.Operator.ADD, 1));
        assertEquals(0, evaluate(3, ArithmeticExpression.Operator.MOD, 2, ArithmeticExpression.Operator.SUB, 1));
        assertEquals(1, evaluate(3, ArithmeticExpression.Operator.MOD, 2, ArithmeticExpression.Operator.DIV, 1));
        assertEquals(1, evaluate(3, ArithmeticExpression.Operator.MOD, 2, ArithmeticExpression.Operator.MOD, 2));
        assertEquals(1, evaluate(3, ArithmeticExpression.Operator.MOD, 2, ArithmeticExpression.Operator.MUL, 1));

        assertEquals(3, evaluate(1, ArithmeticExpression.Operator.MUL, 2, ArithmeticExpression.Operator.ADD, 1));
        assertEquals(1, evaluate(1, ArithmeticExpression.Operator.MUL, 2, ArithmeticExpression.Operator.SUB, 1));
        assertEquals(2, evaluate(2, ArithmeticExpression.Operator.MUL, 2, ArithmeticExpression.Operator.DIV, 2));
        assertEquals(0, evaluate(1, ArithmeticExpression.Operator.MUL, 2, ArithmeticExpression.Operator.MOD, 2));
        assertEquals(4, evaluate(1, ArithmeticExpression.Operator.MUL, 2, ArithmeticExpression.Operator.MUL, 2));
    }

    @Test
    public void requireThatFirstOperatorIsAdd() {
        MathResolver resolver = new MathResolver();
        for (ArithmeticExpression.Operator type : ArithmeticExpression.Operator.values()) {
            if (type == ArithmeticExpression.Operator.ADD) {
                continue;
            }
            try {
                resolver.push(type, newInteger(69));
            } catch (IllegalArgumentException e) {
                assertEquals("First item in an arithmetic operation must be an addition, not " + type, e.getMessage());
            }
        }
    }

    @Test
    public void requireThatNullOperatorThrowsException() {
        try {
            new MathResolver().push(null, newInteger(69));
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    // --------------------------------------------------------------------------------
    //
    // Utilities
    //
    // --------------------------------------------------------------------------------

    private static Expression newInteger(int val) {
        return new SetValueExpression(new IntegerFieldValue(val));
    }

    private static int evaluate(Expression exp) {
        FieldValue val = new ExecutionContext(new SimpleTestAdapter()).execute(exp).getValue();
        assertTrue(val instanceof IntegerFieldValue);
        return ((IntegerFieldValue)val).getInteger();
    }

    private static int evaluate(int lhs, ArithmeticExpression.Operator op, int rhs) {
        MathResolver resolver = new MathResolver();
        resolver.push(ArithmeticExpression.Operator.ADD, newInteger(lhs));
        resolver.push(op, newInteger(rhs));
        return evaluate(resolver.resolve());
    }

    private static int evaluate(int valA,
                                ArithmeticExpression.Operator opB, int valC,
                                ArithmeticExpression.Operator opD, int valE)
    {
        MathResolver resolver = new MathResolver();
        resolver.push(ArithmeticExpression.Operator.ADD, newInteger(valA));
        resolver.push(opB, newInteger(valC));
        resolver.push(opD, newInteger(valE));
        return evaluate(resolver.resolve());
    }
}

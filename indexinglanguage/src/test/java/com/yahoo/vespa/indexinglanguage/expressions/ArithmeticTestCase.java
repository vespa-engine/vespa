// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ArithmeticExpression.Operator;
import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;

/**
 * @author Simon Thoresen Hult
 */
public class ArithmeticTestCase {

    @Test
    public void requireThatAccessorsWork() {
        ArithmeticExpression exp = newArithmetic(6, Operator.ADD, 9);
        assertEquals(newLong(6), exp.getLeftHandSide());
        assertEquals(Operator.ADD, exp.getOperator());
        assertEquals(newLong(9), exp.getRightHandSide());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = newArithmetic(6, Operator.ADD, 9);
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(newArithmetic(1, Operator.DIV, 1)));
        assertFalse(exp.equals(newArithmetic(6, Operator.DIV, 1)));
        assertFalse(exp.equals(newArithmetic(6, Operator.ADD, 1)));
        assertEquals(exp, newArithmetic(6, Operator.ADD, 9));
        assertEquals(exp.hashCode(), newArithmetic(6, Operator.ADD, 9).hashCode());
    }

    @Test
    public void requireThatConstructorDoesNotAcceptNull() {
        try {
            newArithmetic(null, Operator.ADD, new SimpleExpression());
            fail();
        } catch (NullPointerException e) {

        }
        try {
            newArithmetic(new SimpleExpression(), null, new SimpleExpression());
            fail();
        } catch (NullPointerException e) {

        }
        try {
            newArithmetic(new SimpleExpression(), Operator.ADD, null);
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    public void requireThatVerifyCallsAreForwarded() {
        assertVerify(SimpleExpression.newOutput(DataType.INT), Operator.ADD,
                     SimpleExpression.newOutput(DataType.INT), null);
        assertVerifyThrows(SimpleExpression.newOutput(null), Operator.ADD,
                           SimpleExpression.newOutput(DataType.INT), null,
                           "Expected any output, but no output is specified");
        assertVerifyThrows(SimpleExpression.newOutput(DataType.INT), Operator.ADD,
                           SimpleExpression.newOutput(null), null,
                           "Expected any output, but no output is specified");
        assertVerifyThrows(SimpleExpression.newOutput(null), Operator.ADD,
                           SimpleExpression.newOutput(null), null,
                           "Expected any output, but no output is specified");
        assertVerifyThrows(SimpleExpression.newOutput(DataType.INT), Operator.ADD,
                           SimpleExpression.newOutput(DataType.STRING), null,
                           "The second argument must be a number, but has type string");
        assertVerifyThrows(SimpleExpression.newOutput(DataType.STRING), Operator.ADD,
                           SimpleExpression.newOutput(DataType.STRING), null,
                           "The first argument must be a number, but has type string");
    }

    @Test
    public void requireThatOperandInputCanBeNull() {
        SimpleExpression reqNull = new SimpleExpression();
        SimpleExpression reqInt = new SimpleExpression(DataType.INT);
        assertNull(newArithmetic(reqNull, Operator.ADD, reqNull).requiredInputType());
        assertEquals(DataType.INT, newArithmetic(reqInt, Operator.ADD, reqNull).requiredInputType());
        assertEquals(DataType.INT, newArithmetic(reqInt, Operator.ADD, reqInt).requiredInputType());
        assertEquals(DataType.INT, newArithmetic(reqNull, Operator.ADD, reqInt).requiredInputType());
    }

    @Test
    public void requireThatOperandsAreInputCompatible() {
        assertVerify(new SimpleExpression(DataType.INT), Operator.ADD,
                     new SimpleExpression(DataType.INT), DataType.INT);
        assertVerifyThrows(new SimpleExpression(DataType.INT), Operator.ADD,
                           new SimpleExpression(DataType.STRING), null,
                           "Operands require conflicting input types, int vs string");
    }

    @Test
    public void requireThatResultIsCalculated() {
        for (int i = 0; i < 50; ++i) {
            LongFieldValue left = new LongFieldValue(i);
            LongFieldValue right = new LongFieldValue(100 - i);
            assertResult(left, Operator.ADD, right, new LongFieldValue(left.getLong() + right.getLong()));
            assertResult(left, Operator.SUB, right, new LongFieldValue(left.getLong() - right.getLong()));
            assertResult(left, Operator.DIV, right, new LongFieldValue(left.getLong() / right.getLong()));
            assertResult(left, Operator.MOD, right, new LongFieldValue(left.getLong() % right.getLong()));
            assertResult(left, Operator.MUL, right, new LongFieldValue(left.getLong() * right.getLong()));
        }
    }

    @Test
    public void requireThatArithmeticWithNullEvaluatesToNull() {
        assertNull(newArithmetic(new SimpleExpression(), Operator.ADD,
                                 new ConstantExpression(new LongFieldValue(69))).execute());
        assertNull(newArithmetic(new ConstantExpression(new LongFieldValue(69)), Operator.ADD,
                                 new SimpleExpression()).execute());
    }

    @Test
    public void requireThatNonNumericOperandThrows() {
        try {
            newArithmetic(new ConstantExpression(new IntegerFieldValue(6)), Operator.ADD,
                          new ConstantExpression(new StringFieldValue("9"))).execute();
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Unsupported operation: [int] + [string]", e.getMessage());
        }
        try {
            newArithmetic(new ConstantExpression(new StringFieldValue("6")), Operator.ADD,
                          new ConstantExpression(new IntegerFieldValue(9))).execute();
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Unsupported operation: [string] + [int]", e.getMessage());
        }
    }

    @Test
    public void requireThatProperNumericalTypeIsUsed() {
        for (Operator op : Operator.values()) {
            assertType(DataType.INT, op, DataType.INT, DataType.INT);
            assertType(DataType.LONG, op, DataType.INT, DataType.LONG);
            assertType(DataType.LONG, op, DataType.LONG, DataType.LONG);
            assertType(DataType.INT, op, DataType.LONG, DataType.LONG);

            assertType(DataType.FLOAT, op, DataType.FLOAT, DataType.FLOAT);
            assertType(DataType.DOUBLE, op, DataType.FLOAT, DataType.DOUBLE);
            assertType(DataType.DOUBLE, op, DataType.DOUBLE, DataType.DOUBLE);
            assertType(DataType.FLOAT, op, DataType.DOUBLE, DataType.DOUBLE);

            assertType(DataType.INT, op, DataType.FLOAT, DataType.FLOAT);
            assertType(DataType.INT, op, DataType.DOUBLE, DataType.DOUBLE);
        }
    }

    private void assertResult(FieldValue lhs, Operator op, FieldValue rhs, FieldValue expected) {
        assertEquals(expected, evaluate(new ConstantExpression(lhs), op,
                                        new ConstantExpression(rhs)));
    }

    private void assertType(DataType lhs, Operator op, DataType rhs, DataType expected) {
        assertEquals(expected, newArithmetic(SimpleExpression.newOutput(lhs), op,
                                             SimpleExpression.newOutput(rhs)).verify());
        assertEquals(expected, newArithmetic(lhs.createFieldValue(6), op,
                                             rhs.createFieldValue(9)).execute().getDataType());
    }

    private static FieldValue evaluate(Expression lhs, Operator op, Expression rhs) {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        new ArithmeticExpression(lhs, op, rhs).execute(ctx);
        return ctx.getCurrentValue();
    }

    private static ArithmeticExpression newArithmetic(long lhs, Operator op, long rhs) {
        return newArithmetic(new LongFieldValue(lhs), op, new LongFieldValue(rhs));
    }

    private static ArithmeticExpression newArithmetic(FieldValue lhs, Operator op, FieldValue rhs) {
        return newArithmetic(new ConstantExpression(lhs), op, new ConstantExpression(rhs));
    }

    private static ArithmeticExpression newArithmetic(Expression lhs, Operator op, Expression rhs) {
        return new ArithmeticExpression(lhs, op, rhs);
    }

    private static ConstantExpression newLong(long val) {
        return new ConstantExpression(new LongFieldValue(val));
    }

    private static void assertVerify(Expression lhs, Operator op, Expression rhs, DataType val) {
        new ArithmeticExpression(lhs, op, rhs).verify(val);
    }

    private static void assertVerifyThrows(Expression lhs, Operator op, Expression rhs, DataType val,
                                           String expectedException) {
        ArithmeticExpression expression = null;
        try {
            expression = new ArithmeticExpression(lhs, op, rhs);
            expression.verify(val);
            fail("Expected exception");
        } catch (VerificationException e) {
            String expressionString = expression == null ? "of type '" + ArithmeticExpression.class.getSimpleName() + "'"
                                                         : "'" + expression + "'";
            assertEquals("Invalid expression " + expressionString + ": " + expectedException, e.getMessage());
        }
    }
}

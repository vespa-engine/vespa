// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
                           "Attempting to perform arithmetic on a null value.");
        assertVerifyThrows(SimpleExpression.newOutput(DataType.INT), Operator.ADD,
                           SimpleExpression.newOutput(null), null,
                           "Attempting to perform arithmetic on a null value.");
        assertVerifyThrows(SimpleExpression.newOutput(null), Operator.ADD,
                           SimpleExpression.newOutput(null), null,
                           "Attempting to perform arithmetic on a null value.");
        assertVerifyThrows(SimpleExpression.newOutput(DataType.INT), Operator.ADD,
                           SimpleExpression.newOutput(DataType.STRING), null,
                           "Attempting to perform unsupported arithmetic: [int] + [string]");
        assertVerifyThrows(SimpleExpression.newOutput(DataType.STRING), Operator.ADD,
                           SimpleExpression.newOutput(DataType.STRING), null,
                           "Attempting to perform unsupported arithmetic: [string] + [string]");
    }

    @Test
    public void requireThatOperandInputCanBeNull() {
        SimpleExpression reqNull = new SimpleExpression();
        SimpleExpression reqInt = new SimpleExpression().setRequiredInput(DataType.INT);
        assertNull(newArithmetic(reqNull, Operator.ADD, reqNull).requiredInputType());
        assertEquals(DataType.INT, newArithmetic(reqInt, Operator.ADD, reqNull).requiredInputType());
        assertEquals(DataType.INT, newArithmetic(reqInt, Operator.ADD, reqInt).requiredInputType());
        assertEquals(DataType.INT, newArithmetic(reqNull, Operator.ADD, reqInt).requiredInputType());
    }

    @Test
    public void requireThatOperandsAreInputCompatible() {
        assertVerify(new SimpleExpression().setRequiredInput(DataType.INT), Operator.ADD,
                     new SimpleExpression().setRequiredInput(DataType.INT), DataType.INT);
        assertVerifyThrows(new SimpleExpression().setRequiredInput(DataType.INT), Operator.ADD,
                           new SimpleExpression().setRequiredInput(DataType.STRING), null,
                           "Operands require conflicting input types, int vs string.");
    }

    @Test
    public void requireThatResultIsCalculated() {
        for (int i = 0; i < 50; ++i) {
            LongFieldValue lhs = new LongFieldValue(i);
            LongFieldValue rhs = new LongFieldValue(100 - i);
            assertResult(lhs, Operator.ADD, rhs, new LongFieldValue(lhs.getLong() + rhs.getLong()));
            assertResult(lhs, Operator.SUB, rhs, new LongFieldValue(lhs.getLong() - rhs.getLong()));
            assertResult(lhs, Operator.DIV, rhs, new LongFieldValue(lhs.getLong() / rhs.getLong()));
            assertResult(lhs, Operator.MOD, rhs, new LongFieldValue(lhs.getLong() % rhs.getLong()));
            assertResult(lhs, Operator.MUL, rhs, new LongFieldValue(lhs.getLong() * rhs.getLong()));
        }
    }

    @Test
    public void requireThatArithmeticWithNullEvaluatesToNull() {
        assertNull(newArithmetic(new SimpleExpression(), Operator.ADD,
                                 new SetValueExpression(new LongFieldValue(69))).execute());
        assertNull(newArithmetic(new SetValueExpression(new LongFieldValue(69)), Operator.ADD,
                                 new SimpleExpression()).execute());
    }

    @Test
    public void requireThatNonNumericOperandThrows() {
        try {
            newArithmetic(new SetValueExpression(new IntegerFieldValue(6)), Operator.ADD,
                          new SetValueExpression(new StringFieldValue("9"))).execute();
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Unsupported operation: [int] + [string]", e.getMessage());
        }
        try {
            newArithmetic(new SetValueExpression(new StringFieldValue("6")), Operator.ADD,
                          new SetValueExpression(new IntegerFieldValue(9))).execute();
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
        assertEquals(expected, evaluate(new SetValueExpression(lhs), op,
                                        new SetValueExpression(rhs)));
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
        return ctx.getValue();
    }

    private static ArithmeticExpression newArithmetic(long lhs, Operator op, long rhs) {
        return newArithmetic(new LongFieldValue(lhs), op, new LongFieldValue(rhs));
    }

    private static ArithmeticExpression newArithmetic(FieldValue lhs, Operator op, FieldValue rhs) {
        return newArithmetic(new SetValueExpression(lhs), op, new SetValueExpression(rhs));
    }

    private static ArithmeticExpression newArithmetic(Expression lhs, Operator op, Expression rhs) {
        return new ArithmeticExpression(lhs, op, rhs);
    }

    private static SetValueExpression newLong(long val) {
        return new SetValueExpression(new LongFieldValue(val));
    }

    private static void assertVerify(Expression lhs, Operator op, Expression rhs, DataType val) {
        new ArithmeticExpression(lhs, op, rhs).verify(val);
    }

    private static void assertVerifyThrows(Expression lhs, Operator op, Expression rhs, DataType val,
                                           String expectedException) {
        try {
            new ArithmeticExpression(lhs, op, rhs).verify(val);
            fail();
        } catch (VerificationException e) {
            assertEquals(expectedException, e.getMessage());
        }
    }
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.*;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static com.yahoo.vespa.indexinglanguage.expressions.IfThenExpression.Comparator;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class IfThenTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Expression lhs = new AttributeExpression("lhs");
        Expression rhs = new AttributeExpression("rhs");
        Expression ifTrue = new AttributeExpression("ifTrue");
        Expression ifFalse = new AttributeExpression("ifFalse");
        IfThenExpression exp = new IfThenExpression(lhs, Comparator.EQ, rhs, ifTrue, ifFalse);
        assertSame(lhs, exp.getLeftHandSide());
        assertSame(rhs, exp.getRightHandSide());
        assertEquals(Comparator.EQ, exp.getComparator());
        assertSame(ifTrue, exp.getIfTrueExpression());
        assertSame(ifFalse, exp.getIfFalseExpression());
    }

    @Test
    public void requireThatRequiredInputTypeCompatibilityIsVerified() {
        Expression exp = newRequiredInput(DataType.STRING, Comparator.EQ, DataType.STRING,
                                          DataType.STRING, DataType.STRING);
        assertVerify(DataType.STRING, exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected string input, got null.");
        assertVerifyThrows(DataType.INT, exp, "Expected string input, got int.");
        assertVerifyThrows(null, () -> newRequiredInput(DataType.INT, Comparator.EQ, DataType.STRING,
                                                  DataType.STRING, DataType.STRING),
                           "Operands require conflicting input types, int vs string.");
        assertVerifyThrows(null, () -> newRequiredInput(DataType.STRING, Comparator.EQ, DataType.INT,
                                                  DataType.STRING, DataType.STRING),
                           "Operands require conflicting input types, string vs int.");
        assertVerifyThrows(null, () -> newRequiredInput(DataType.STRING, Comparator.EQ, DataType.STRING,
                                                  DataType.INT, DataType.STRING),
                           "Operands require conflicting input types, string vs int.");
        assertVerifyThrows(null, () -> newRequiredInput(DataType.STRING, Comparator.EQ, DataType.STRING,
                                                  DataType.STRING, DataType.INT),
                           "Operands require conflicting input types, string vs int.");
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        assertVerify(DataType.STRING, new FlattenExpression(), DataType.STRING);
        assertVerifyThrows(null, new FlattenExpression(),
                           "Expected string input, got null.");
        assertVerifyThrows(DataType.INT, new FlattenExpression(),
                           "Expected string input, got int.");
    }

    @Test
    public void requireThatIfFalseDefaultsToNull() {
        IfThenExpression exp = new IfThenExpression(new AttributeExpression("lhs"), Comparator.EQ,
                                                    new AttributeExpression("rhs"), new AttributeExpression("ifTrue"));
        assertNull(exp.getIfFalseExpression());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression lhs = new AttributeExpression("lhs");
        Expression rhs = new AttributeExpression("rhs");
        Expression ifTrue = new AttributeExpression("ifTrue");
        Expression ifFalse = new AttributeExpression("ifFalse");
        Expression exp = new IfThenExpression(lhs, Comparator.EQ, rhs, ifTrue, ifFalse);

        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new IfThenExpression(new IndexExpression("lhs"), Comparator.NE,
                                                    new IndexExpression("rhs"),
                                                    new IndexExpression("ifTrue"),
                                                    new IndexExpression("ifFalse"))));
        assertFalse(exp.equals(new IfThenExpression(new AttributeExpression("lhs"), Comparator.NE,
                                                    new IndexExpression("rhs"),
                                                    new IndexExpression("ifTrue"),
                                                    new IndexExpression("ifFalse"))));
        assertFalse(exp.equals(new IfThenExpression(new AttributeExpression("lhs"), Comparator.EQ,
                                                    new IndexExpression("rhs"),
                                                    new IndexExpression("ifTrue"),
                                                    new IndexExpression("ifFalse"))));
        assertFalse(exp.equals(new IfThenExpression(new AttributeExpression("lhs"), Comparator.EQ,
                                                    new AttributeExpression("rhs"),
                                                    new IndexExpression("ifTrue"),
                                                    new IndexExpression("ifFalse"))));
        assertFalse(exp.equals(new IfThenExpression(new AttributeExpression("lhs"), Comparator.EQ,
                                                    new AttributeExpression("rhs"),
                                                    new AttributeExpression("ifTrue"),
                                                    new IndexExpression("ifFalse"))));
        assertEquals(exp, new IfThenExpression(new AttributeExpression("lhs"), Comparator.EQ,
                                               new AttributeExpression("rhs"),
                                               new AttributeExpression("ifTrue"),
                                               new AttributeExpression("ifFalse")));
        assertEquals(exp.hashCode(), new IfThenExpression(new AttributeExpression("lhs"), Comparator.EQ,
                                                          new AttributeExpression("rhs"),
                                                          new AttributeExpression("ifTrue"),
                                                          new AttributeExpression("ifFalse")).hashCode());
    }

    @Test
    public void requireThatAllChildrenSeeInputValue() {
        FieldValueAdapter adapter = createTestAdapter();
        new StatementExpression(new SetValueExpression(new IntegerFieldValue(69)),
                                new IfThenExpression(new AttributeExpression("lhs"),
                                                     Comparator.EQ,
                                                     new AttributeExpression("rhs"),
                                                     new AttributeExpression("ifTrue"),
                                                     new AttributeExpression("ifFalse"))).execute(adapter);
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("lhs"));
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("rhs"));
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("ifTrue"));
        assertNull(null, adapter.getInputValue("ifFalse"));

        adapter = createTestAdapter();
        new StatementExpression(new SetValueExpression(new IntegerFieldValue(69)),
                                new IfThenExpression(new AttributeExpression("lhs"),
                                                     Comparator.NE,
                                                     new AttributeExpression("rhs"),
                                                     new AttributeExpression("ifTrue"),
                                                     new AttributeExpression("ifFalse"))).execute(adapter);
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("lhs"));
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("rhs"));
        assertNull(null, adapter.getInputValue("ifTrue"));
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("ifFalse"));
    }

    @Test
    public void requireThatElseExpIsOptional() {
        ExecutionContext ctx = new ExecutionContext();
        Expression exp = new IfThenExpression(new SetValueExpression(new IntegerFieldValue(6)),
                                              Comparator.GT,
                                              new SetValueExpression(new IntegerFieldValue(9)),
                                              new SetValueExpression(new StringFieldValue("69")));
        FieldValue val = ctx.setValue(new IntegerFieldValue(96)).execute(exp).getValue();
        assertTrue(val instanceof IntegerFieldValue);
        assertEquals(96, ((IntegerFieldValue)val).getInteger());
    }

    @Test
    public void requireThatNonNumericValuesUseFieldValueCompareTo() {
        FieldValue small = new MyFieldValue(6);
        FieldValue large = new MyFieldValue(9);

        assertCmpTrue(small, Comparator.EQ, small);
        assertCmpFalse(small, Comparator.NE, small);
        assertCmpFalse(small, Comparator.GT, small);
        assertCmpTrue(small, Comparator.GE, small);
        assertCmpFalse(small, Comparator.LT, small);
        assertCmpTrue(small, Comparator.LE, small);

        assertCmpFalse(small, Comparator.EQ, large);
        assertCmpTrue(small, Comparator.NE, large);
        assertCmpFalse(small, Comparator.GT, large);
        assertCmpFalse(small, Comparator.GE, large);
        assertCmpTrue(small, Comparator.LT, large);
        assertCmpTrue(small, Comparator.LE, large);
    }

    @Test
    public void requireThatNumericValuesSupportNumericCompareTo() {
        List<NumericFieldValue> sixes = Arrays.asList(new ByteFieldValue((byte)6),
                                                      new DoubleFieldValue(6.0),
                                                      new FloatFieldValue(6.0f),
                                                      new IntegerFieldValue(6),
                                                      new LongFieldValue(6L));
        List<NumericFieldValue> nines = Arrays.asList(new ByteFieldValue((byte)9),
                                                      new DoubleFieldValue(9.0),
                                                      new FloatFieldValue(9.0f),
                                                      new IntegerFieldValue(9),
                                                      new LongFieldValue(9L));
        for (NumericFieldValue lhs : sixes) {
            for (NumericFieldValue rhs : sixes) {
                assertCmpTrue(lhs, Comparator.EQ, rhs);
                assertCmpFalse(lhs, Comparator.NE, rhs);
                assertCmpFalse(lhs, Comparator.GT, rhs);
                assertCmpTrue(lhs, Comparator.GE, rhs);
                assertCmpFalse(lhs, Comparator.LT, rhs);
                assertCmpTrue(lhs, Comparator.LE, rhs);
            }
            for (NumericFieldValue rhs : nines) {
                assertCmpFalse(lhs, Comparator.EQ, rhs);
                assertCmpTrue(lhs, Comparator.NE, rhs);
                assertCmpFalse(lhs, Comparator.GT, rhs);
                assertCmpFalse(lhs, Comparator.GE, rhs);
                assertCmpTrue(lhs, Comparator.LT, rhs);
                assertCmpTrue(lhs, Comparator.LE, rhs);
            }
        }
        for (NumericFieldValue lhs : nines) {
            for (NumericFieldValue rhs : nines) {
                assertCmpTrue(lhs, Comparator.EQ, rhs);
                assertCmpFalse(lhs, Comparator.NE, rhs);
                assertCmpFalse(lhs, Comparator.GT, rhs);
                assertCmpTrue(lhs, Comparator.GE, rhs);
                assertCmpFalse(lhs, Comparator.LT, rhs);
                assertCmpTrue(lhs, Comparator.LE, rhs);
            }
            for (NumericFieldValue rhs : sixes) {
                assertCmpFalse(lhs, Comparator.EQ, rhs);
                assertCmpTrue(lhs, Comparator.NE, rhs);
                assertCmpTrue(lhs, Comparator.GT, rhs);
                assertCmpTrue(lhs, Comparator.GE, rhs);
                assertCmpFalse(lhs, Comparator.LT, rhs);
                assertCmpFalse(lhs, Comparator.LE, rhs);
            }
        }
    }

    @Test
    public void requireThatNullLeftOrRightHandSideEvaluatesToNull() {
        Expression exp = new IfThenExpression(new GetVarExpression("lhs"), Comparator.EQ, new GetVarExpression("rhs"),
                                              new SetValueExpression(new StringFieldValue("true")),
                                              new SetValueExpression(new StringFieldValue("false")));
        assertEquals(new StringFieldValue("true"),
                     exp.execute(new ExecutionContext().setVariable("lhs", new IntegerFieldValue(69))
                                                       .setVariable("rhs", new IntegerFieldValue(69))));
        assertEquals(new StringFieldValue("false"),
                     exp.execute(new ExecutionContext().setVariable("lhs", new IntegerFieldValue(6))
                                                       .setVariable("rhs", new IntegerFieldValue(9))));
        assertNull(exp.execute(new ExecutionContext().setVariable("lhs", new IntegerFieldValue(69))));
        assertNull(exp.execute(new ExecutionContext().setVariable("rhs", new IntegerFieldValue(69))));
    }

    private static void assertCmpTrue(FieldValue lhs, Comparator cmp, FieldValue rhs) {
        assertTrue(evaluateIfThen(lhs, cmp, rhs));
    }

    private static void assertCmpFalse(FieldValue lhs, Comparator cmp, FieldValue rhs) {
        assertFalse(evaluateIfThen(lhs, cmp, rhs));
    }

    private static boolean evaluateIfThen(FieldValue lhs, Comparator cmp, FieldValue rhs) {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        new StatementExpression(
                new SetValueExpression(new IntegerFieldValue(1)),
                new IfThenExpression(new SetValueExpression(lhs), cmp, new SetValueExpression(rhs),
                                     new SetVarExpression("true"),
                                     new SetVarExpression("false"))).execute(ctx);
        return ctx.getVariable("true") != null;
    }

    private static FieldValueAdapter createTestAdapter() {
        return new SimpleTestAdapter(new Field("lhs", DataType.INT),
                                     new Field("rhs", DataType.INT),
                                     new Field("ifTrue", DataType.INT),
                                     new Field("ifFalse", DataType.INT));
    }

    private static Expression newRequiredInput(DataType lhs, Comparator cmp, DataType rhs, DataType ifTrue, DataType ifFalse) {
        return new IfThenExpression(new SimpleExpression(lhs), cmp,
                                    new SimpleExpression(rhs),
                                    new SimpleExpression(ifTrue),
                                    ifFalse != null ? new SimpleExpression(ifFalse) : null);
    }

    private static class MyFieldValue extends FieldValue {

        final Integer val;

        MyFieldValue(int val) {
            this.val = val;
        }

        @Override
        public DataType getDataType() {
            return null;
        }

        @Override
        @Deprecated
        public void printXml(XmlStream xml) {

        }

        @Override
        public void clear() {

        }

        @Override
        public void assign(Object o) {

        }

        @Override
        public void serialize(Field field, FieldWriter writer) {

        }

        @Override
        public void deserialize(Field field, FieldReader reader) {

        }

        @Override
        public int compareTo(FieldValue rhs) {
            if (!(rhs instanceof MyFieldValue)) {
                throw new AssertionError();
            }
            return val.compareTo(((MyFieldValue)rhs).val);
        }
    }
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.*;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
@SuppressWarnings({ "rawtypes" })
public class ForEachTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Expression innerExp = new AttributeExpression("foo");
        ForEachExpression exp = new ForEachExpression(innerExp);
        assertSame(innerExp, exp.getInnerExpression());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression innerExp = new AttributeExpression("foo");
        Expression exp = new ForEachExpression(innerExp);
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new ForEachExpression(new AttributeExpression("bar"))));
        assertEquals(exp, new ForEachExpression(innerExp));
        assertEquals(exp.hashCode(), new ForEachExpression(innerExp).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ForEachExpression(SimpleExpression.newConversion(DataType.INT, DataType.STRING));
        assertVerify(DataType.getArray(DataType.INT), exp, DataType.getArray(DataType.STRING));
        assertVerifyThrows(null, exp, "Expected any input, got null.");
        assertVerifyThrows(DataType.INT, exp, "Expected Array, Struct or WeightedSet input, got int.");
        assertVerifyThrows(DataType.getArray(DataType.STRING), exp, "Expected int input, got string.");
    }

    @Test
    public void requireThatStructFieldCompatibilityIsVerified() {
        StructDataType type = new StructDataType("my_struct");
        type.addField(new Field("foo", DataType.INT));
        assertVerify(type, new ForEachExpression(new SimpleExpression()), type);
        assertVerifyThrows(type, new ForEachExpression(SimpleExpression.newConversion(DataType.STRING, DataType.INT)),
                           "Expected string input, got int.");
        assertVerifyThrows(type, new ForEachExpression(SimpleExpression.newConversion(DataType.INT, DataType.STRING)),
                           "Expected int output, got string.");
    }

    @Test
    public void requireThatEachTokenIsExecutedSeparately() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        Array<StringFieldValue> arr = new Array<>(DataType.getArray(DataType.STRING));
        arr.add(new StringFieldValue("6"));
        arr.add(new StringFieldValue("9"));
        ctx.setValue(arr);

        MyCollector exp = new MyCollector();
        new ForEachExpression(exp).execute(ctx);

        assertEquals(2, exp.lst.size());

        FieldValue val = exp.lst.get(0);
        assertTrue(val instanceof StringFieldValue);
        assertEquals("6", ((StringFieldValue)val).getString());

        val = exp.lst.get(1);
        assertTrue(val instanceof StringFieldValue);
        assertEquals("9", ((StringFieldValue)val).getString());
    }

    @Test
    public void requireThatCreatedOutputTypeDependsOnInnerExpression() {
        assertNull(new ForEachExpression(new SimpleExpression()).createdOutputType());
        assertNotNull(new ForEachExpression(new SetValueExpression(new IntegerFieldValue(69))).createdOutputType());
    }

    @Test
    public void requireThatArrayCanBeConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        Array<StringFieldValue> before = new Array<>(DataType.getArray(DataType.STRING));
        before.add(new StringFieldValue("6"));
        before.add(new StringFieldValue("9"));
        ctx.setValue(before);

        new ForEachExpression(new ToIntegerExpression()).execute(ctx);
        FieldValue val = ctx.getValue();
        assertTrue(val instanceof Array);

        Array after = (Array)val;
        assertEquals(2, after.size());
        assertEquals(new IntegerFieldValue(6), after.get(0));
        assertEquals(new IntegerFieldValue(9), after.get(1));
    }

    @Test
    public void requireThatEmptyArrayCanBeConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new Array<StringFieldValue>(DataType.getArray(DataType.STRING)));

        new ForEachExpression(new ToIntegerExpression()).execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof Array);
        assertEquals(DataType.INT, ((Array)val).getDataType().getNestedType());
        assertTrue(((Array)val).isEmpty());
    }

    @Test
    public void requireThatIllegalInputValueThrows() {
        try {
            new ForEachExpression(new SimpleExpression()).execute(new StringFieldValue("foo"));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected Array, Struct or WeightedSet input, got string.", e.getMessage());
        }
    }

    @Test
    public void requireThatArrayWithNullCanBeConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        Array<StringFieldValue> arr = new Array<>(DataType.getArray(DataType.STRING));
        arr.add(new StringFieldValue("foo"));
        ctx.setValue(arr);

        new ForEachExpression(SimpleExpression.newConversion(DataType.STRING, DataType.INT)
                                              .setExecuteValue(null)).execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof Array);
        assertEquals(DataType.INT, ((Array)val).getDataType().getNestedType());
        assertTrue(((Array)val).isEmpty());
    }

    @Test
    public void requireThatWsetCanBeConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        WeightedSet<StringFieldValue> before = new WeightedSet<>(DataType.getWeightedSet(DataType.STRING));
        before.put(new StringFieldValue("6"), 9);
        before.put(new StringFieldValue("9"), 6);
        ctx.setValue(before);

        new ForEachExpression(new ToIntegerExpression()).execute(ctx);
        FieldValue val = ctx.getValue();
        assertTrue(val instanceof WeightedSet);

        WeightedSet after = (WeightedSet)val;
        assertEquals(2, after.size());
        assertEquals(Integer.valueOf(9), after.get(new IntegerFieldValue(6)));
        assertEquals(Integer.valueOf(6), after.get(new IntegerFieldValue(9)));
    }

    @Test
    public void requireThatEmptyWsetCanBeConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new WeightedSet<StringFieldValue>(DataType.getWeightedSet(DataType.STRING)));

        new ForEachExpression(new ToIntegerExpression()).execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof WeightedSet);
        assertEquals(DataType.INT, ((WeightedSet)val).getDataType().getNestedType());
        assertTrue(((WeightedSet)val).isEmpty());
    }

    @Test
    public void requireThatStructContentCanBeConverted() {
        StructDataType type = new StructDataType("my_type");
        type.addField(new Field("my_str", DataType.STRING));
        Struct struct = new Struct(type);
        struct.setFieldValue("my_str", new StringFieldValue("  foo  "));

        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(struct);

        new ForEachExpression(new TrimExpression()).execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof Struct);
        assertEquals(type, val.getDataType());
        assertEquals(new StringFieldValue("foo"), ((Struct)val).getFieldValue("my_str"));
    }

    @Test
    public void requireThatIncompatibleStructFieldsFailToValidate() {
        StructDataType type = new StructDataType("my_type");
        type.addField(new Field("my_int", DataType.INT));

        VerificationContext ctx = new VerificationContext(new SimpleTestAdapter());
        ctx.setValue(type);

        try {
            new ForEachExpression(new ToArrayExpression()).verify(ctx);
            fail();
        } catch (VerificationException e) {
            assertEquals("Expected int output, got Array<int>.", e.getMessage());
        }
    }

    @Test
    public void requireThatIncompatibleStructFieldsFailToExecute() {
        StructDataType type = new StructDataType("my_type");
        type.addField(new Field("my_int", DataType.INT));
        Struct struct = new Struct(type);
        struct.setFieldValue("my_int", new IntegerFieldValue(69));

        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(struct);

        try {
            new ForEachExpression(new ToArrayExpression()).execute(ctx);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Class class com.yahoo.document.datatypes.Array not applicable to an class " +
                         "com.yahoo.document.datatypes.IntegerFieldValue instance.", e.getMessage());
        }
    }

    private static class MyCollector extends Expression {

        List<FieldValue> lst = new LinkedList<>();

        @Override
        protected void doExecute(ExecutionContext ctx) {
            lst.add(ctx.getValue());
        }

        @Override
        protected void doVerify(VerificationContext context) {

        }

        @Override
        public DataType requiredInputType() {
            return null;
        }

        @Override
        public DataType createdOutputType() {
            return null;
        }
    }
}

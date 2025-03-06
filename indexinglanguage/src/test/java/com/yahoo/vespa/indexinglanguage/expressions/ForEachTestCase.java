// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
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
    public void requireThatStructFieldCompatibilityIsVerified() {
        StructDataType type = new StructDataType("my_struct");
        type.addField(new Field("foo", DataType.INT));
        assertVerify(type, new ForEachExpression(new SimpleExpression(DataType.INT, DataType.INT)), type);
        assertVerifyThrows("Invalid expression 'SimpleExpression': Expected string input, got int", type, new ForEachExpression(SimpleExpression.newConversion(DataType.STRING, DataType.INT)));
        assertVerifyThrows("Invalid expression 'for_each { SimpleExpression }': Struct field 'foo' has type int but expression produces string", type, new ForEachExpression(SimpleExpression.newConversion(DataType.INT, DataType.STRING)));
    }

    @Test
    public void requireThatEachTokenIsExecutedSeparately() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        Array<StringFieldValue> arr = new Array<>(DataType.getArray(DataType.STRING));
        arr.add(new StringFieldValue("6"));
        arr.add(new StringFieldValue("9"));
        ctx.setCurrentValue(arr);

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
    public void requireThatArrayCanBeConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        Array<StringFieldValue> before = new Array<>(DataType.getArray(DataType.STRING));
        before.add(new StringFieldValue("6"));
        before.add(new StringFieldValue("9"));
        ctx.setCurrentValue(before);

        new ForEachExpression(new ToIntegerExpression()).execute(ctx);
        FieldValue val = ctx.getCurrentValue();
        assertTrue(val instanceof Array);

        Array after = (Array)val;
        assertEquals(2, after.size());
        assertEquals(new IntegerFieldValue(6), after.get(0));
        assertEquals(new IntegerFieldValue(9), after.get(1));
    }

    @Test
    public void requireThatIllegalInputValueThrows() {
        try {
            new ForEachExpression(new SimpleExpression()).execute(new StringFieldValue("foo"));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected Array, Struct, WeightedSet or Map input, got string", e.getMessage());
        }
    }

    @Test
    public void requireThatWsetCanBeConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        WeightedSet<StringFieldValue> before = new WeightedSet<>(DataType.getWeightedSet(DataType.STRING));
        before.put(new StringFieldValue("6"), 9);
        before.put(new StringFieldValue("9"), 6);
        ctx.setCurrentValue(before);

        new ForEachExpression(new ToIntegerExpression()).execute(ctx);
        FieldValue val = ctx.getCurrentValue();
        assertTrue(val instanceof WeightedSet);

        WeightedSet after = (WeightedSet)val;
        assertEquals(2, after.size());
        assertEquals(Integer.valueOf(9), after.get(new IntegerFieldValue(6)));
        assertEquals(Integer.valueOf(6), after.get(new IntegerFieldValue(9)));
    }

    private static class MyCollector extends Expression {

        List<FieldValue> lst = new LinkedList<>();

        @Override
        protected void doExecute(ExecutionContext context) {
            lst.add(context.getCurrentValue());
        }

        @Override
        protected void doVerify(VerificationContext context) {

        }

        @Override
        public DataType createdOutputType() {
            return null;
        }
    }

}

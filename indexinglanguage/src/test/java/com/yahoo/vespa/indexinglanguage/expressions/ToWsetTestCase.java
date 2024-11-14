// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "rawtypes" })
public class ToWsetTestCase {

    @Test
    public void requireThatAccessorsWork() {
        assertAccessors(false, false);
        assertAccessors(false, true);
        assertAccessors(true, false);
        assertAccessors(true, true);
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ToWsetExpression(true, true);
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new ToWsetExpression(false, false)));
        assertFalse(exp.equals(new ToWsetExpression(true, false)));
        assertFalse(exp.equals(new ToWsetExpression(false, true)));
        assertEquals(exp, new ToWsetExpression(true, true));
        assertEquals(exp.hashCode(), new ToWsetExpression(true, true).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        assertVerify(false, false);
        assertVerify(false, true);
        assertVerify(true, false);
        assertVerify(true, true);
    }

    @Test
    public void requireThatValueIsConverted() {
        assertConvert(false, false);
        assertConvert(false, true);
        assertConvert(true, false);
        assertConvert(true, true);
    }

    private static void assertVerify(boolean createIfNonExistent, boolean removeIfZero) {
        Expression expression = new ToWsetExpression(createIfNonExistent, removeIfZero);
        ExpressionAssert.assertVerify(DataType.INT, expression,
                                      DataType.getWeightedSet(DataType.INT, createIfNonExistent, removeIfZero));
        ExpressionAssert.assertVerify(DataType.STRING, expression,
                                      DataType.getWeightedSet(DataType.STRING, createIfNonExistent, removeIfZero));
        assertVerifyThrows("Invalid expression '" + expression + "': " +
                           "Expected any input, but no input is specified", null, expression);
    }

    private static void assertConvert(boolean createIfNonExistent, boolean removeIfZero) {
        ExecutionContext context = new ExecutionContext(new SimpleTestAdapter());
        context.setCurrentValue(new StringFieldValue("69")).execute(new ToWsetExpression(createIfNonExistent, removeIfZero));

        FieldValue value = context.getCurrentValue();
        assertEquals(WeightedSet.class, value.getClass());

        WeightedSet wset = (WeightedSet)value;
        WeightedSetDataType type = wset.getDataType();
        assertEquals(DataType.STRING, type.getNestedType());
        assertEquals(createIfNonExistent, type.createIfNonExistent());
        assertEquals(removeIfZero, type.removeIfZero());

        assertEquals(1, wset.size());
        assertEquals(Integer.valueOf(1), wset.get(new StringFieldValue("69")));
    }

    private static void assertAccessors(boolean createIfNonExistent, boolean removeIfZero) {
        ToWsetExpression expression = new ToWsetExpression(createIfNonExistent, removeIfZero);
        assertEquals(createIfNonExistent, expression.getCreateIfNonExistent());
        assertEquals(removeIfZero, expression.getRemoveIfZero());
    }

}

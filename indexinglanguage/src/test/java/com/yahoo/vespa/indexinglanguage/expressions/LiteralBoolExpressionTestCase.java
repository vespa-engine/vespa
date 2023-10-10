// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class LiteralBoolExpressionTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        assertFalse(new LiteralBoolExpression(true).equals(new Object()));
        assertEquals(new LiteralBoolExpression(true), new LiteralBoolExpression(true));
        assertEquals(new LiteralBoolExpression(false), new LiteralBoolExpression(false));
        assertNotEquals(new LiteralBoolExpression(true), new LiteralBoolExpression(false));
        assertEquals(new LiteralBoolExpression(false).hashCode(), new LiteralBoolExpression(false).hashCode());
        assertEquals(new LiteralBoolExpression(true).hashCode(), new LiteralBoolExpression(true).hashCode());
        assertNotEquals(new LiteralBoolExpression(true).hashCode(), new LiteralBoolExpression(false).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new LiteralBoolExpression(true);
        assertVerify(DataType.INT, exp, DataType.BOOL);
        assertVerify(DataType.STRING, exp, DataType.BOOL);
    }

    @Test
    public void testToString() {
        assertEquals("false", new LiteralBoolExpression(false).toString());
        assertEquals("true", new LiteralBoolExpression(true).toString());
    }

    @Test
    public void requireThatTrueBecomesTrue() {
        ExecutionContext context = new ExecutionContext(new SimpleTestAdapter());
        context.execute(new LiteralBoolExpression(true));
        FieldValue value = context.getValue();
        assertTrue(value instanceof BoolFieldValue);
        assertTrue(((BoolFieldValue)value).getBoolean());
    }

    @Test
    public void requireThatFalseBecomesFalse() {
        ExecutionContext context = new ExecutionContext(new SimpleTestAdapter());
        context.execute(new LiteralBoolExpression(false));
        FieldValue value = context.getValue();
        assertTrue(value instanceof BoolFieldValue);
        assertFalse(((BoolFieldValue)value).getBoolean());
    }

}

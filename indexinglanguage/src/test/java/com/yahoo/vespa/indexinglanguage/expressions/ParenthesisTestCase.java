// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ParenthesisTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Expression innerExp = new AttributeExpression("foo");
        ParenthesisExpression exp = new ParenthesisExpression(innerExp);
        assertSame(innerExp, exp.getInnerExpression());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression innerExp = new AttributeExpression("foo");
        Expression exp = new ParenthesisExpression(innerExp);
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new ParenthesisExpression(new AttributeExpression("bar"))));
        assertEquals(exp, new ParenthesisExpression(innerExp));
        assertEquals(exp.hashCode(), new ParenthesisExpression(innerExp).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ParenthesisExpression(SimpleExpression.newConversion(DataType.INT, DataType.STRING));
        assertVerify(DataType.INT, exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected int input, but no input is specified");
        assertVerifyThrows(DataType.STRING, exp, "Expected int input, got string");
    }

    @Test
    public void requireThatNestedExpressionIsRun() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter(new Field("in", DataType.STRING)));
        ctx.setOutputValue(null, "in", new StringFieldValue("69"));
        new ParenthesisExpression(new InputExpression("in")).execute(ctx);

        assertTrue(ctx.getValue() instanceof StringFieldValue);
        assertEquals("69", ((StringFieldValue)ctx.getValue()).getString());
    }
}

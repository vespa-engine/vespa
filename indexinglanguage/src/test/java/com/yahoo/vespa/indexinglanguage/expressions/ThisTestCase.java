// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Simon Thoresen Hult
 */
public class ThisTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ThisExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ThisExpression());
        assertEquals(exp.hashCode(), new ThisExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        assertVerify(DataType.INT, new ThisExpression(), DataType.INT);
        assertVerify(DataType.STRING, new ThisExpression(), DataType.STRING);
        assertVerifyThrows("Invalid expression 'this': Expected input, but no input is provided", null, new ThisExpression());
    }

    @Test
    public void requireThatValueIsPreserved() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setCurrentValue(new StringFieldValue("69"));
        new ThisExpression().execute(ctx);

        assertEquals(new StringFieldValue("69"), ctx.getCurrentValue());
    }
}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class LowerCaseTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new LowerCaseExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new LowerCaseExpression());
        assertEquals(exp.hashCode(), new LowerCaseExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new LowerCaseExpression();
        assertVerify(DataType.STRING, exp, DataType.STRING);
        assertVerifyThrows("Invalid expression 'lowercase': Expected string input, but no input is provided", null, exp);
        assertVerifyThrows("Invalid expression 'lowercase': Expected string input, got int", DataType.INT, exp);
    }

    @Test
    public void requireThatStringIsLowerCased() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setCurrentValue(new StringFieldValue("FOO"));
        new LowerCaseExpression().execute(ctx);

        FieldValue val = ctx.getCurrentValue();
        assertTrue(val instanceof StringFieldValue);
        assertEquals("foo", ((StringFieldValue)val).getString());
    }
}

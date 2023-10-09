// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "rawtypes" })
public class SplitTestCase {

    @Test
    public void requireThatAccessorsWork() {
        SplitExpression exp = new SplitExpression("foo");
        assertEquals("foo", exp.getSplitPattern().toString());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new SplitExpression("foo");
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new SplitExpression("bar")));
        assertEquals(exp, new SplitExpression("foo"));
        assertEquals(exp.hashCode(), new SplitExpression("foo").hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new SplitExpression(";");
        assertVerify(DataType.STRING, exp, DataType.getArray(DataType.STRING));
        assertVerifyThrows(null, exp, "Expected string input, but no input is specified");
        assertVerifyThrows(DataType.INT, exp, "Expected string input, got int");
    }

    @Test
    public void requireThatValueIsSplit() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("6;9"));
        new SplitExpression(";").execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val.getDataType().equals(DataType.getArray(DataType.STRING)));
        assertTrue(val instanceof Array);

        Array arr = (Array)val;
        assertEquals(new StringFieldValue("6"), arr.get(0));
        assertEquals(new StringFieldValue("9"), arr.get(1));
    }

    @Test
    public void requireThatNullInputProducesNullOutput() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        new SplitExpression(";").execute(ctx);

        assertNull(ctx.getValue());
    }

    @Test
    public void requireThatEmptyInputProducesEmptyArray() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue(""));
        new SplitExpression(";").execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val.getDataType().equals(DataType.getArray(DataType.STRING)));
        assertTrue(val instanceof Array);
        assertEquals(0, ((Array)val).size());
    }
}

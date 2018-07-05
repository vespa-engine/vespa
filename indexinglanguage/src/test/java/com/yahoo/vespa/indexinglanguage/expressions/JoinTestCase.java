// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class JoinTestCase {

    @Test
    public void requireThatAccessorsWork() {
        JoinExpression exp = new JoinExpression("foo");
        assertEquals("foo", exp.getDelimiter());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new JoinExpression("foo");
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new JoinExpression("bar")));
        assertEquals(exp, new JoinExpression("foo"));
        assertEquals(exp.hashCode(), new JoinExpression("foo").hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new JoinExpression(";");
        assertVerify(DataType.getArray(DataType.INT), exp, DataType.STRING);
        assertVerify(DataType.getArray(DataType.STRING), exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected any input, got null.");
        assertVerifyThrows(DataType.INT, exp, "Expected Array input, got int.");
    }

    @Test
    public void requireThatValueIsJoined() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        Array<StringFieldValue> arr = new Array<>(DataType.getArray(DataType.STRING));
        arr.add(new StringFieldValue("6"));
        arr.add(new StringFieldValue("9"));
        ctx.setValue(arr);

        new JoinExpression(";").execute(ctx);
        assertEquals(new StringFieldValue("6;9"), ctx.getValue());
    }

    @Test
    public void requireThatNonArrayInputThrows() {
        try {
            new JoinExpression(";").execute(new StringFieldValue("foo"));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected Array input, got string.", e.getMessage());
        }
    }

    @Test
    public void requireThatAccessorWorks() {
        JoinExpression exp = new JoinExpression(";");
        assertEquals(";", exp.getDelimiter());
    }
}

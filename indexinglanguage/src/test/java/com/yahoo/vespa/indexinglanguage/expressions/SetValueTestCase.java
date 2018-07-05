// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class SetValueTestCase {

    @Test
    public void requireThatAccessorsWork() {
        FieldValue foo = new StringFieldValue("foo");
        SetValueExpression exp = new SetValueExpression(foo);
        assertSame(foo, exp.getValue());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        FieldValue foo = new StringFieldValue("foo");
        Expression exp = new SetValueExpression(foo);
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new SetValueExpression(new StringFieldValue("bar"))));
        assertEquals(exp, new SetValueExpression(foo));
        assertEquals(exp.hashCode(), new SetValueExpression(foo).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new SetValueExpression(new StringFieldValue("foo"));
        assertVerify(null, exp, DataType.STRING);
        assertVerify(DataType.INT, exp, DataType.STRING);
        assertVerify(DataType.STRING, exp, DataType.STRING);
    }

    @Test
    public void requireThatNullValueThrowsException() {
        try {
            new SetValueExpression(null);
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    public void requireThatValueIsSet() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        new SetValueExpression(new StringFieldValue("69")).execute(ctx);
        assertEquals(new StringFieldValue("69"), ctx.getValue());
    }

    @Test
    public void requireThatLongFieldValueGetsATrailingL() {
        assertEquals("69L", new SetValueExpression(new LongFieldValue(69)).toString());
    }
}

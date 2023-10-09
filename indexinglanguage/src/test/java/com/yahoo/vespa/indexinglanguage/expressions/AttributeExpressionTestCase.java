// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.OutputAssert.assertExecute;
import static com.yahoo.vespa.indexinglanguage.expressions.OutputAssert.assertVerify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Simon Thoresen Hult
 */
public class AttributeExpressionTestCase {

    @Test
    public void requireThatAccessorsWork() {
        AttributeExpression exp = new AttributeExpression("foo");
        assertEquals("foo", exp.getFieldName());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new AttributeExpression("foo");
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new AttributeExpression("bar")));
        assertFalse(exp.equals(new IndexExpression("foo")));
        assertEquals(exp, new AttributeExpression("foo"));
        assertEquals(exp.hashCode(), new AttributeExpression("foo").hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        assertVerify(new AttributeExpression("foo"));
    }

    @Test
    public void requireThatExpressionCanBeExecuted() {
        assertExecute(new AttributeExpression("foo"));
    }
}

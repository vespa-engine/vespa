// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.OutputAssert.assertExecute;
import static com.yahoo.vespa.indexinglanguage.expressions.OutputAssert.assertVerify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Simon Thoresen Hult
 */
public class IndexExpressionTestCase {

    @Test
    public void requireThatAccessorsWork() {
        IndexExpression exp = new IndexExpression("foo");
        assertEquals("foo", exp.getFieldName());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression lhs = new IndexExpression("foo");
        assertFalse(lhs.equals(new Object()));
        assertFalse(lhs.equals(new AttributeExpression("foo")));
        assertFalse(lhs.equals(new IndexExpression("bar")));
        assertEquals(lhs, new IndexExpression("foo"));
        assertEquals(lhs.hashCode(), new IndexExpression("foo").hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        assertVerify(new IndexExpression("foo"));
    }

    @Test
    public void requireThatExpressionCanBeExecuted() {
        assertExecute(new IndexExpression("foo"));
    }
}

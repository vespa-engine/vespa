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
public class SummaryExpressionTestCase {

    @Test
    public void requireThatAccessorsWork() {
        SummaryExpression exp = new SummaryExpression("foo");
        assertEquals("foo", exp.getFieldName());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new SummaryExpression("foo");
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new AttributeExpression("foo")));
        assertFalse(exp.equals(new SummaryExpression("bar")));
        assertEquals(exp, new SummaryExpression("foo"));
        assertEquals(exp.hashCode(), new SummaryExpression("foo").hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        assertVerify(new SummaryExpression("foo"));
    }

    @Test
    public void requireThatExpressionCanBeExecuted() {
        assertExecute(new SummaryExpression("foo"));
    }
}

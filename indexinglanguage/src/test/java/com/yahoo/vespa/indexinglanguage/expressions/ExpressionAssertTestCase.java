// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertNotNull;

/**
 * @author Simon Thoresen Hult
 */
public class ExpressionAssertTestCase {

    @Test
    public void requireThatAssertVerifyMethodThrowsWhenAppropriate() {
        Throwable thrown = null;
        try {
            assertVerify(DataType.INT, new SimpleExpression(), DataType.STRING);
        } catch (Throwable t) {
            thrown = t;
        }
        assertNotNull(thrown);

        thrown = null;
        try {
            assertVerifyThrows("unchecked expected exception message", DataType.INT, new SimpleExpression()
                              );
        } catch (Throwable t) {
            thrown = t;
        }
        assertNotNull(thrown);

        thrown = null;
        try {
            assertVerifyThrows("wrong expected exception message", DataType.INT, SimpleExpression.newRequired(DataType.STRING)
                              );
        } catch (Throwable t) {
            thrown = t;
        }
        assertNotNull(thrown);
    }
}

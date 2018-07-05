// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.OutputAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.OutputAssert.assertVerifyThrows;
import static org.junit.Assert.assertNotNull;

/**
 * @author Simon Thoresen Hult
 */
public class OutputAssertTestCase {

    @Test
    public void requireThatAssertVerifyMethodThrowsWhenAppropriate() {
        Throwable thrown = null;
        try {
            assertVerify(null, DataType.INT, SimpleExpression.newRequired(DataType.STRING));
        } catch (Throwable t) {
            thrown = t;
        }
        assertNotNull(thrown);

        thrown = null;
        try {
            assertVerifyThrows(null, DataType.INT, SimpleExpression.newRequired(DataType.INT),
                               "unchecked expected exception message");
        } catch (Throwable t) {
            thrown = t;
        }
        assertNotNull(thrown);

        thrown = null;
        try {
            assertVerifyThrows(null, DataType.INT, SimpleExpression.newRequired(DataType.STRING),
                               "wrong expected exception message");
        } catch (Throwable t) {
            thrown = t;
        }
        assertNotNull(thrown);
    }
}

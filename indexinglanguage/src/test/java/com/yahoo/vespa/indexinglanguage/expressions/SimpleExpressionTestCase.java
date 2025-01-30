// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleExpressionTestCase {

    @Test
    public void requireThatAccessorsWork() {
        SimpleExpression exp = new SimpleExpression();
        assertNull(exp.createdOutputType());
        assertNull(exp.execute());
        exp.verify(new SimpleTestAdapter());

        assertEquals(DataType.INT, new SimpleExpression().setCreatedOutput(DataType.INT).createdOutputType());
        new SimpleExpression().setVerifyValue(DataType.INT).verify(new SimpleTestAdapter());
        assertEquals(new IntegerFieldValue(69),
                     new SimpleExpression().setExecuteValue(new IntegerFieldValue(69)).execute());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new SimpleExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new SimpleExpression());
        assertEquals(exp.hashCode(), new SimpleExpression().hashCode());

        exp = new SimpleExpression().setExecuteValue(null);
        assertFalse(exp.equals(new SimpleExpression()));
        assertEquals(exp, new SimpleExpression().setExecuteValue(null));

        exp = new SimpleExpression().setExecuteValue(new IntegerFieldValue(69));
        assertFalse(exp.equals(new SimpleExpression().setExecuteValue(new IntegerFieldValue(96))));
        assertEquals(exp, new SimpleExpression().setExecuteValue(new IntegerFieldValue(69)));

        exp = new SimpleExpression().setVerifyValue(null);
        assertFalse(exp.equals(new SimpleExpression()));
        assertEquals(exp, new SimpleExpression().setVerifyValue(null));

        exp = new SimpleExpression().setVerifyValue(DataType.INT);
        assertFalse(exp.equals(new SimpleExpression().setVerifyValue(DataType.STRING)));
        assertEquals(exp, new SimpleExpression().setVerifyValue(DataType.INT));

        exp = new SimpleExpression(DataType.INT);
        assertEquals(exp, new SimpleExpression(DataType.INT));

        exp = new SimpleExpression().setCreatedOutput(DataType.INT);
        assertFalse(exp.equals(new SimpleExpression().setCreatedOutput(DataType.STRING)));
        assertEquals(exp, new SimpleExpression().setCreatedOutput(DataType.INT));
    }
}

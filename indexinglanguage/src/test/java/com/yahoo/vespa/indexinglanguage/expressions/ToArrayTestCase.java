// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "rawtypes" })
public class ToArrayTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ToArrayExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ToArrayExpression());
        assertEquals(exp.hashCode(), new ToArrayExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ToArrayExpression();
        assertVerify(DataType.INT, exp, DataType.getArray(DataType.INT));
        assertVerify(DataType.STRING, exp, DataType.getArray(DataType.STRING));
        assertVerifyThrows(null, exp, "Expected any input, but no input is specified");
    }

    @Test
    public void requireThatValueIsConverted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("69")).execute(new ToArrayExpression());

        FieldValue val = ctx.getValue();
        assertEquals(Array.class, val.getClass());

        Array arr = (Array)val;
        ArrayDataType type = arr.getDataType();
        assertEquals(DataType.STRING, type.getNestedType());
        assertEquals(1, arr.size());
        assertEquals(new StringFieldValue("69"), arr.get(0));
    }
}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ExpressionTestCase {

    @Test
    public void requireThatInputTypeIsCheckedBeforeExecute() {
        assertExecute(newRequiredInput(DataType.INT), null);
        assertExecute(newRequiredInput(DataType.INT), new IntegerFieldValue(69));
        assertExecuteThrows(newRequiredInput(DataType.INT), new StringFieldValue("foo"),
                            new IllegalArgumentException("expected int input, got string"));
    }

    @Test
    public void requireThatOutputTypeIsCheckedAfterExecute() {
        assertExecute(newCreatedOutput(DataType.INT, (FieldValue)null), null);
        assertExecute(newCreatedOutput(DataType.INT, new IntegerFieldValue(69)), null);
        assertExecuteThrows(newCreatedOutput(DataType.INT, new StringFieldValue("foo")), null,
                            new IllegalStateException("expected int output, got string"));
    }

    @Test
    public void requireThatInputTypeIsCheckedBeforeVerify() {
        assertVerify(newRequiredInput(DataType.INT), DataType.INT);
        assertVerifyThrows(newRequiredInput(DataType.INT), null,
                           "Expected int input, but no input is specified");
        assertVerifyThrows(newRequiredInput(DataType.INT), UnresolvedDataType.INSTANCE,
                           "Failed to resolve input type");
        assertVerifyThrows(newRequiredInput(DataType.INT), DataType.STRING,
                           "Expected int input, got string");
    }

    @Test
    public void requireThatOutputTypeIsCheckedAfterVerify() {
        assertVerify(newCreatedOutput(DataType.INT, DataType.INT), null);
        assertVerifyThrows(newCreatedOutput(DataType.INT, (DataType)null), null,
                           "Expected int output, but no output is specified");
        assertVerifyThrows(newCreatedOutput(DataType.INT, UnresolvedDataType.INSTANCE), null,
                           "Failed to resolve output type");
        assertVerifyThrows(newCreatedOutput(DataType.INT, DataType.STRING), null,
                           "Expected int output, got string");
    }

    @Test
    public void requireThatEqualsMethodWorks() {
        assertTrue(Expression.equals(null, null));
        assertTrue(Expression.equals(1, 1));
        assertFalse(Expression.equals(1, 2));
        assertFalse(Expression.equals(1, null));
        assertFalse(Expression.equals(null, 2));
    }

    private static Expression newRequiredInput(DataType requiredInput) {
        return new SimpleExpression(requiredInput);
    }

    private static Expression newCreatedOutput(DataType createdOutput, FieldValue actualOutput) {
        return new SimpleExpression().setCreatedOutput(createdOutput).setExecuteValue(actualOutput);
    }

    private static Expression newCreatedOutput(DataType createdOutput, DataType actualOutput) {
        return new SimpleExpression().setCreatedOutput(createdOutput).setVerifyValue(actualOutput);
    }

    private static void assertExecute(Expression exp, FieldValue val) {
        exp.execute(val);
    }

    private static void assertExecuteThrows(Expression exp, FieldValue val, Exception expectedException) {
        try {
            exp.execute(val);
            fail();
        } catch (RuntimeException e) {
            assertEquals(expectedException.getClass(), e.getClass());
            assertTrue(e.getMessage().contains(expectedException.getMessage()));
        }
    }

    private static void assertVerify(Expression exp, DataType val) {
        exp.verify(val);
    }

    private static void assertVerifyThrows(Expression exp, DataType val, String expectedException) {
        try {
            exp.verify(val);
            fail();
        } catch (VerificationException e) {
            assertEquals(expectedException, e.getMessage());
        }
    }
}

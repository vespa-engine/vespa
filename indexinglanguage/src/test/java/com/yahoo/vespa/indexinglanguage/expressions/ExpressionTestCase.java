// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ExpressionTestCase {

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
                           "Invalid expression 'SimpleExpression': Expected int input, but no input is provided");
        assertVerifyThrows(newRequiredInput(DataType.INT), UnresolvedDataType.INSTANCE,
                           "Invalid expression 'SimpleExpression': Expected int input, got unresolved");
        assertVerifyThrows(newRequiredInput(DataType.INT), DataType.STRING,
                           "Invalid expression 'SimpleExpression': Expected int input, got string");
    }

    private static Expression newRequiredInput(DataType requiredInput) {
        return new SimpleExpression(requiredInput);
    }

    private static Expression newCreatedOutput(DataType createdOutput, FieldValue actualOutput) {
        return new SimpleExpression().setCreatedOutput(createdOutput).setExecuteValue(actualOutput);
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
        var context = new VerificationContext(new SimpleTestAdapter()).setCurrentType(val);
        exp.setInputType(val, context);
        exp.verify(context);
    }

    private static void assertVerifyThrows(Expression exp, DataType val, String expectedException) {
        try {
            assertVerify(exp, val);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals(expectedException, e.getMessage());
        }
    }

}

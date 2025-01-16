// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
class ExpressionAssert {

    public static void assertVerifyCtx(Expression expression, DataType expectedValueAfter, VerificationContext context) {
        assertEquals(expectedValueAfter, expression.verify(context));
    }

    public static void assertVerify(DataType valueBefore, Expression expression, DataType expectedValueAfter) {
        assertVerifyCtx(expression, expectedValueAfter, new VerificationContext().setCurrentType(valueBefore));
    }

    public static void assertVerifyThrows(String expectedMessage, DataType valueBefore, Expression expression) {
        assertVerifyThrows(expectedMessage, expression, new VerificationContext().setCurrentType(valueBefore));
    }

    interface CreateExpression {
        Expression create();
    }
    public static void assertVerifyThrows(String expectedMessage, DataType valueBefore, CreateExpression createExpression) {
        assertVerifyThrows(expectedMessage, createExpression, new VerificationContext().setCurrentType(valueBefore));
    }

    public static void assertVerifyThrows(String expectedMessage, CreateExpression createExp, VerificationContext context) {
        try {
            Expression exp = createExp.create();
            exp.verify(context);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }
    public static void assertVerifyThrows(String expectedMessage, Expression expression, VerificationContext context) {
        try {
            expression.verify(context);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

}

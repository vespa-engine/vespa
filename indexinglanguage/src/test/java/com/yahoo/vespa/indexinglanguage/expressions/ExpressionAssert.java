// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
class ExpressionAssert {

    public static void assertVerifyCtx(Expression expression, TypeContext context) {
        expression.resolve(context);
    }

    public static void assertVerify(DataType inputType, Expression expression, DataType outputType) {
        var context = new TypeContext(new SimpleTestAdapter());
        assertVerifyCtx(expression, context);
        assertEquals(outputType, expression.setInputType(inputType, context));
        assertEquals(inputType, expression.setOutputType(outputType, context));
    }

    public static void assertVerifyThrows(String expectedMessage, DataType valueBefore, Expression expression) {
        assertVerifyThrows(expectedMessage, expression, valueBefore, new TypeContext(new SimpleTestAdapter()));
    }

    interface CreateExpression {
        Expression create();
    }
    public static void assertVerifyThrows(String expectedMessage, DataType valueBefore, CreateExpression createExpression) {
        assertVerifyThrows(expectedMessage, createExpression, new TypeContext(new SimpleTestAdapter()));
    }

    public static void assertVerifyThrows(String expectedMessage, CreateExpression createExp, TypeContext context) {
        try {
            Expression exp = createExp.create();
            exp = new StatementExpression(new ConstantExpression(new StringFieldValue("test")), exp);
            exp.resolve(context);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }
    public static void assertVerifyThrows(String expectedMessage, Expression expression, DataType inputType, TypeContext context) {
        try {
            expression.setInputType(inputType, context);
            expression.resolve(context);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

}

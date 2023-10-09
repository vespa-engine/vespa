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

    public static void assertVerifyCtx(VerificationContext ctx, Expression exp, DataType expectedValueAfter) {
        assertEquals(expectedValueAfter, exp.verify(ctx));
    }

    public static void assertVerify(DataType valueBefore, Expression exp, DataType expectedValueAfter) {
        assertVerifyCtx(new VerificationContext().setValueType(valueBefore), exp, expectedValueAfter);
    }

    public static void assertVerifyThrows(DataType valueBefore, Expression exp, String expectedException) {
        assertVerifyCtxThrows(new VerificationContext().setValueType(valueBefore), exp, expectedException);
    }

    interface CreateExpression {
        Expression create();
    }
    public static void assertVerifyThrows(DataType valueBefore, CreateExpression createExp, String expectedException) {
        assertVerifyCtxThrows(new VerificationContext().setValueType(valueBefore), createExp, expectedException);
    }

    public static void assertVerifyCtxThrows(VerificationContext ctx, CreateExpression createExp, String expectedException) {
        try {
            Expression exp = createExp.create();
            exp.verify(ctx);
            fail();
        } catch (VerificationException e) {
            if (!Pattern.matches(expectedException, e.getMessage())) {
                assertEquals(expectedException, e.getMessage());
            }
        }
    }
    public static void assertVerifyCtxThrows(VerificationContext ctx, Expression exp, String expectedException) {
        try {
            exp.verify(ctx);
            fail();
        } catch (VerificationException e) {
            if (!Pattern.matches(expectedException, e.getMessage())) {
                assertEquals(expectedException, e.getMessage());
            }
        }
    }
}

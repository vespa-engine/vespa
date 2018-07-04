// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        assertVerifyCtx(new VerificationContext().setValue(valueBefore), exp, expectedValueAfter);
    }

    public static void assertVerifyThrows(DataType valueBefore, Expression exp, String expectedException) {
        assertVerifyCtxThrows(new VerificationContext().setValue(valueBefore), exp, expectedException);
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

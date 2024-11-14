// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Language;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Simon Thoresen Hult
 */
public class SetLanguageTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new SetLanguageExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new SetLanguageExpression());
        assertEquals(exp.hashCode(), new SetLanguageExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new SetLanguageExpression();
        assertVerify(DataType.STRING, exp, DataType.STRING);
        assertVerifyThrows("Invalid expression 'set_language': Expected string input, but no input is specified", null, exp);
        assertVerifyThrows("Invalid expression 'set_language': Expected string input, got int", DataType.INT, exp);
    }

    @Test
    public void testsettingEnglish() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setCurrentValue(new StringFieldValue("en"));
        new SetLanguageExpression().execute(ctx);
        assertEquals(Language.ENGLISH, ctx.getLanguage());
    }

    @Test
    public void testSettingUnknown() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setCurrentValue(new StringFieldValue("unknown"));
        new SetLanguageExpression().execute(ctx);
        assertEquals(Language.UNKNOWN, ctx.getLanguage());
    }

}

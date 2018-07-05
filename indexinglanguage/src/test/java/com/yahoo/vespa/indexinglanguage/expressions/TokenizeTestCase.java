// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import com.yahoo.vespa.indexinglanguage.linguistics.AnnotatorConfig;
import org.junit.Test;
import org.mockito.Mockito;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class TokenizeTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Linguistics linguistics = new SimpleLinguistics();
        AnnotatorConfig config = new AnnotatorConfig();
        TokenizeExpression exp = new TokenizeExpression(linguistics, config);
        assertSame(linguistics, exp.getLinguistics());
        assertSame(config, exp.getConfig());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        AnnotatorConfig config = new AnnotatorConfig().setLanguage(Language.ARABIC);
        Expression exp = new TokenizeExpression(new SimpleLinguistics(), config);
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new TokenizeExpression(Mockito.mock(Linguistics.class),
                                                      new AnnotatorConfig().setLanguage(Language.SPANISH))));
        assertFalse(exp.equals(new TokenizeExpression(new SimpleLinguistics(),
                                                      new AnnotatorConfig().setLanguage(Language.SPANISH))));
        assertEquals(exp, new TokenizeExpression(new SimpleLinguistics(), config));
        assertEquals(exp.hashCode(), new TokenizeExpression(new SimpleLinguistics(), config).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new TokenizeExpression(new SimpleLinguistics(), new AnnotatorConfig());
        assertVerify(DataType.STRING, exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected string input, got null.");
        assertVerifyThrows(DataType.INT, exp, "Expected string input, got int.");
    }

    @Test
    public void requireThatValueIsAnnotated() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("foo"));
        new TokenizeExpression(new SimpleLinguistics(), new AnnotatorConfig()).execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof StringFieldValue);
        assertNotNull(((StringFieldValue)val).getSpanTree(SpanTrees.LINGUISTICS));
    }
}

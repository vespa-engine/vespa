// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Transformer;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;

import org.junit.Test;
import org.mockito.Mockito;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class NormalizeTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Linguistics linguistics = new SimpleLinguistics();
        NormalizeExpression exp = new NormalizeExpression(linguistics);
        assertSame(linguistics, exp.getLinguistics());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Linguistics linguistics = new SimpleLinguistics();
        NormalizeExpression exp = new NormalizeExpression(linguistics);
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new NormalizeExpression(Mockito.mock(Linguistics.class))));
        assertEquals(exp, new NormalizeExpression(linguistics));
        assertEquals(exp.hashCode(), new NormalizeExpression(linguistics).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new NormalizeExpression(new SimpleLinguistics());
        assertVerify(DataType.STRING, exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected string input, but no input is specified");
        assertVerifyThrows(DataType.INT, exp, "Expected string input, got int");
    }

    @Test
    public void requireThatInputIsNormalized() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setLanguage(Language.ENGLISH);
        ctx.setValue(new StringFieldValue("b\u00e9yonc\u00e8"));
        new NormalizeExpression(new SimpleLinguistics()).execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof StringFieldValue);
        assertEquals("beyonce", ((StringFieldValue)val).getString());
    }

    class MyMockTransformer implements Transformer {
        public boolean first = true;
        @Override
        public String accentDrop(String input, Language language) {
            if (first) {
                first = false;
                return "\u0008";
            } else {
                return input.replace(' ', '/');
            }
        }
    }        

    boolean getFirst(Transformer t) {
        assertTrue(t instanceof MyMockTransformer);
        var mmt = (MyMockTransformer)t;
        return mmt.first;
    }

    class MyMockLinguistics extends SimpleLinguistics {
        private Transformer transformer = new MyMockTransformer();
        @Override
        public Transformer getTransformer() {
            return transformer;
        }

        @Override
        public boolean equals(Linguistics other) { return (other instanceof MyMockLinguistics); }
    }

    @Test
    public void requireThatBadNormalizeRetries() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setLanguage(Language.ENGLISH);
        ctx.setValue(new StringFieldValue("bad norm"));
        var linguistics = new MyMockLinguistics();
        assertTrue(getFirst(linguistics.getTransformer()));
        new NormalizeExpression(linguistics).execute(ctx);
        FieldValue val = ctx.getValue();
        assertTrue(val instanceof StringFieldValue);
        assertEquals("bad/norm", ((StringFieldValue)val).getString());
        assertFalse(getFirst(linguistics.getTransformer()));
    }

    @Test
    public void requireThatEmptyIsNop() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setLanguage(Language.ENGLISH);
        var orig = new StringFieldValue("");
        ctx.setValue(orig);
        var linguistics = new MyMockLinguistics();
        assertTrue(getFirst(linguistics.getTransformer()));
        new NormalizeExpression(linguistics).execute(ctx);
        FieldValue val = ctx.getValue();
        assertTrue(val == orig);
        assertTrue(getFirst(linguistics.getTransformer()));
    }

}

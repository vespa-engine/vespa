// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.language.simple.SimpleTokenizer;
import com.yahoo.vespa.indexinglanguage.FieldValuesFactory;
import com.yahoo.vespa.indexinglanguage.ScriptTester;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import com.yahoo.vespa.indexinglanguage.UpdateFieldValues;
import com.yahoo.vespa.indexinglanguage.linguistics.AnnotatorConfig;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({"removal"})
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
        assertVerifyThrows("Invalid expression 'tokenize': Expected string input, but no input is provided", null, exp);
        assertVerifyThrows("Invalid expression 'tokenize': Expected string input, got int", DataType.INT, exp);
    }

    @Test
    public void requireThatValueIsAnnotated() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setCurrentValue(new StringFieldValue("foo"));
        new TokenizeExpression(new SimpleLinguistics(), new AnnotatorConfig()).execute(ctx);

        FieldValue value = ctx.getCurrentValue();
        assertTrue(value instanceof StringFieldValue);
        assertNotNull(((StringFieldValue)value).getSpanTree(SpanTrees.LINGUISTICS));
        assertEquals("foo", ((StringFieldValue)value).getSpanTree(SpanTrees.LINGUISTICS).getStringFieldValue().getString());
        assertEquals(3, ((StringFieldValue)value).getSpanTree(SpanTrees.LINGUISTICS).spanList().childIterator().next().getLength());
    }

    @Test
    public void requireThatCasingCanBePreserved() {
        // Lowercasing (default)
        {
            ExecutionContext context = new ExecutionContext(new SimpleTestAdapter());
            context.setCurrentValue(new StringFieldValue("mIXed"));
            new TokenizeExpression(new SimpleLinguistics(), new AnnotatorConfig()).execute(context);
            StringFieldValue value = (StringFieldValue)context.getCurrentValue();
            assertNotNull(value.getSpanTree(SpanTrees.LINGUISTICS));
            assertEquals("mIXed", value.getSpanTree(SpanTrees.LINGUISTICS).getStringFieldValue().getString());
            assertEquals("mixed", value.getSpanTree(SpanTrees.LINGUISTICS).iterator().next().getFieldValue().getWrappedValue());
        }

        // Preserve casing
        {
            ExecutionContext context = new ExecutionContext(new SimpleTestAdapter());
            context.setCurrentValue(new StringFieldValue("mIXed"));
            new TokenizeExpression(new SimpleLinguistics(), new AnnotatorConfig().setLowercase(false)).execute(context);
            StringFieldValue value = (StringFieldValue)context.getCurrentValue();
            assertNotNull(value.getSpanTree(SpanTrees.LINGUISTICS));
            assertEquals("mIXed", value.getSpanTree(SpanTrees.LINGUISTICS).getStringFieldValue().getString());
            assertNull(value.getSpanTree(SpanTrees.LINGUISTICS).iterator().next().getFieldValue());
        }
    }

    @Test
    public void requireThatLongWordIsDropped() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setCurrentValue(new StringFieldValue("foo"));
        new TokenizeExpression(new SimpleLinguistics(), new AnnotatorConfig().setMaxTokenLength(2)).execute(ctx);

        FieldValue val = ctx.getCurrentValue();
        assertTrue(val instanceof StringFieldValue);
        assertNull(((StringFieldValue)val).getSpanTree(SpanTrees.LINGUISTICS));
    }

    @Test
    public void testLinguisticsProfile() {
        String script = """
        { clear_state | guard { input myString | tokenize normalize profile:"p1" stem:"BEST" | index myString; } }
        """;

        var tester = new ScriptTester();
        var mockLinguistics = new MockLinguistics();
        var expression = tester.scriptFrom(script, mockLinguistics);

        DocumentType docType = new DocumentType("test");
        Field field1 = new Field("myString", DataType.STRING);
        docType.addField(field1);

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(field1);
        adapter.setValue("myString", new StringFieldValue("Hello, world!"));
        expression.resolve(adapter);
        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        assertEquals("p1", mockLinguistics.lastLinguisticsProfile);
    }

    private static class MockLinguistics extends SimpleLinguistics {

        String lastLinguisticsProfile = null;

        @Override
        public Tokenizer getTokenizer() {
            return new MockTokenizer();
        }

        private class MockTokenizer extends SimpleTokenizer {

            @Override
            public Iterable<Token> tokenize(String input, LinguisticsParameters parameters) {
                lastLinguisticsProfile = parameters.profile();
                return super.tokenize(input, parameters);
            }

        }

    }

}

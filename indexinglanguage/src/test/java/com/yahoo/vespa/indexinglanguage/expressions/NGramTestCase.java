// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.annotation.*;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.TokenType;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Iterator;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author bratseth
 */
public class NGramTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Linguistics linguistics = new SimpleLinguistics();
        NGramExpression exp = new NGramExpression(linguistics, 69);
        assertSame(linguistics, exp.getLinguistics());
        assertEquals(69, exp.getGramSize());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Linguistics linguistics = new SimpleLinguistics();
        NGramExpression exp = new NGramExpression(linguistics, 69);
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new NGramExpression(Mockito.mock(Linguistics.class), 96)));
        assertFalse(exp.equals(new NGramExpression(linguistics, 96)));
        assertEquals(exp, new NGramExpression(linguistics, 69));
        assertEquals(exp.hashCode(), new NGramExpression(new SimpleLinguistics(), 69).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new NGramExpression(new SimpleLinguistics(), 69);
        assertVerify(DataType.STRING, exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected string input, but no input is specified");
        assertVerifyThrows(DataType.INT, exp, "Expected string input, got int");
    }

    @Test
    public void testNGrams() {
        ExecutionContext context = new ExecutionContext(new SimpleTestAdapter());
        context.setValue(new StringFieldValue("en gul Bille sang... "));
        new NGramExpression(new SimpleLinguistics(), 3).execute(context);

        StringFieldValue value = (StringFieldValue)context.getValue();
        assertEquals("Grams are pure annotations - field value is unchanged",
                     "en gul Bille sang... ", value.getString());
        SpanTree gramTree = value.getSpanTree(SpanTrees.LINGUISTICS);
        assertNotNull(gramTree);
        SpanList grams = (SpanList)gramTree.getRoot();
        Iterator<SpanNode> i = grams.childIterator();
        assertSpan(0, 2, true, i, gramTree);  // en
        assertSpan(2, 1, false, i, gramTree); // <space>
        assertSpan(3, 3, true, i, gramTree);  // gul
        assertSpan(6, 1, false, i, gramTree); // <space>
        assertSpan(7, 3, true, i, gramTree, "bil");  // Bil
        assertSpan(8, 3, true, i, gramTree);
        assertSpan(9, 3, true, i, gramTree);
        assertSpan(12, 1, false, i, gramTree); // <space>
        assertSpan(13, 3, true, i, gramTree);
        assertSpan(14, 3, true, i, gramTree);
        assertSpan(17, 4, false, i, gramTree); // <...space>
        assertFalse(i.hasNext());
    }

    @Test
    public void requireThatExecuteCanBeCalledMultipleTimes() {
        ExecutionContext context = new ExecutionContext(new SimpleTestAdapter());
        context.setValue(new StringFieldValue("some random text string"));
        NGramExpression expression = new NGramExpression(new SimpleLinguistics(), 3);

        expression.execute(context);
        SpanTree firstTree = ((StringFieldValue)context.getValue()).getSpanTree(SpanTrees.LINGUISTICS);
        assertNotNull(firstTree);

        expression.execute(context);
        SpanTree secondTree = ((StringFieldValue)context.getValue()).getSpanTree(SpanTrees.LINGUISTICS);
        // The span tree instance should be the same.
        assertEquals(firstTree, secondTree);
    }

    private void assertSpan(int from, int length, boolean gram, Iterator<SpanNode> i, SpanTree tree) {
        assertSpan(from, length, gram, i, tree, null);
    }

    private void assertSpan(int from, int length, boolean gram, Iterator<SpanNode> i, SpanTree tree, String termValue) {
        if (!i.hasNext()) {
            fail("No more spans");
        }
        SpanNode gramSpan = i.next();
        assertEquals("gram start", from, gramSpan.getFrom());
        assertEquals("gram length", length, gramSpan.getLength());
        assertTrue(gramSpan.isLeafNode());
        Iterator<Annotation> annotations = tree.iterator(gramSpan);
        Annotation typeAnnotation = annotations.next();
        assertEquals(AnnotationTypes.TOKEN_TYPE, typeAnnotation.getType());
        int typeInt = ((IntegerFieldValue)typeAnnotation.getFieldValue()).getInteger();
        if (gram) {
            assertEquals(TokenType.ALPHABETIC.getValue(), typeInt);
            Annotation termAnnotation = annotations.next();
            assertEquals(AnnotationTypes.TERM, termAnnotation.getType());
            if (termValue == null) {
                assertNull(termAnnotation.getFieldValue());
            } else {
                assertEquals(termValue, ((StringFieldValue)termAnnotation.getFieldValue()).getString());
            }
        } else { // gap between grams
            assertEquals(TokenType.PUNCTUATION.getValue(), typeInt);
        }
    }
}

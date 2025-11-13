// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.linguistics;

import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationTypes;
import com.yahoo.document.annotation.SimpleIndexingAnnotations;
import com.yahoo.document.annotation.Span;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenType;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleToken;
import com.yahoo.vespa.indexinglanguage.linguistics.LinguisticsAnnotator.TermOccurrences;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests that compare SimpleIndexingAnnotations output with full SpanTree output
 * to ensure they produce equivalent results.
 *
 * @author arnej
 */
public class SimpleAnnotationsComparisonTestCase {

    @Test
    public void testTokenEqualToOriginal() {
        // Token where term equals original - should have no term override
        String text = "foo";
        Token token = new SimpleToken("foo").setTokenString("foo").setType(TokenType.ALPHABETIC).setOffset(0);

        compareAnnotations(text, List.of(token), new AnnotatorConfig());
    }

    @Test
    public void testTokenDifferentCase() {
        // Token where term differs only in case - should have term override with lowercase
        String text = "Foo";
        Token token = new SimpleToken("Foo").setTokenString("foo").setType(TokenType.ALPHABETIC).setOffset(0);

        compareAnnotations(text, List.of(token), new AnnotatorConfig());
    }

    @Test
    public void testTokenSlightlyDifferent() {
        // Token where term is stemmed/modified - should have term override
        String text = "running";
        Token token = new SimpleToken("running").setTokenString("run").setType(TokenType.ALPHABETIC).setOffset(0);

        compareAnnotations(text, List.of(token), new AnnotatorConfig());
    }

    @Test
    public void testTokenWithMultipleStems() {
        // Token with multiple stems - should create multiple annotations (StemMode.ALL)
        String text = "cars";
        SimpleToken token = SimpleToken.fromStems("cars", List.of("car", "cars", "vehicle"));
        token.setOffset(0);

        compareAnnotations(text, List.of(token), new AnnotatorConfig().setStemMode("ALL"));
    }

    @Test
    public void testMultipleTokensVariety() {
        // Mix of different token types to test all paths
        String text = "The Running Dogs";
        List<Token> tokens = List.of(
            // Token equal to original (after lowercase)
            new SimpleToken("The").setTokenString("the").setType(TokenType.ALPHABETIC).setOffset(0),
            // Token with stemming
            new SimpleToken("Running").setTokenString("run").setType(TokenType.ALPHABETIC).setOffset(4),
            // Token equal to original
            new SimpleToken("Dogs").setTokenString("dog").setType(TokenType.ALPHABETIC).setOffset(12)
        );

        compareAnnotations(text, tokens, new AnnotatorConfig());
    }

    @Test
    public void testMultipleTokensWithStemModeAll() {
        String text = "The Running Dogs";

        SimpleToken token1 = new SimpleToken("The").setTokenString("the").setType(TokenType.ALPHABETIC);
        token1.setOffset(0);

        SimpleToken token2 = SimpleToken.fromStems("Running", List.of("run", "running"));
        token2.setOffset(4);

        SimpleToken token3 = SimpleToken.fromStems("Dogs", List.of("dog", "dogs", "canine"));
        token3.setOffset(12);

        List<Token> tokens = List.of(token1, token2, token3);

        compareAnnotations(text, tokens, new AnnotatorConfig().setStemMode("ALL").setLowercase(true));
    }

    @Test
    public void testCasePreservationWithStemModeAll() {
        // Test that case preservation works correctly with StemMode.ALL
        String text = "Tesla";
        Token token = new SimpleToken("Tesla").setTokenString("tesla").setType(TokenType.ALPHABETIC).setOffset(0);

        // With lowercase=false, should preserve "Tesla" as indexableOriginal
        compareAnnotations(text, List.of(token), new AnnotatorConfig().setStemMode("ALL").setLowercase(false));

        // With lowercase=true, should use "tesla" as indexableOriginal
        compareAnnotations(text, List.of(token), new AnnotatorConfig().setStemMode("ALL").setLowercase(true));
    }

    @Test
    public void testEmptyTokenString() {
        // Token with null or empty token string should be skipped
        String text = "...";
        Token token = new SimpleToken("...").setTokenString(null).setType(TokenType.PUNCTUATION).setOffset(0);

        compareAnnotations(text, List.of(token), new AnnotatorConfig());
    }

    @Test
    public void testNonIndexableToken() {
        // Non-indexable tokens should be skipped
        String text = "foo";
        Token token = new SimpleToken("foo").setTokenString("foo").setType(TokenType.PUNCTUATION).setOffset(0);

        compareAnnotations(text, List.of(token), new AnnotatorConfig());
    }

    @Test
    public void testComplexScenario() {
        // A realistic scenario with multiple token types
        String text = "Tesla's Model-X cars";

        SimpleToken token1 = SimpleToken.fromStems("Tesla's", List.of("tesla", "teslas"));
        token1.setOffset(0);

        SimpleToken token2 = SimpleToken.fromStems("Model-X", List.of("modelx", "model"));
        token2.setOffset(8);

        SimpleToken token3 = SimpleToken.fromStems("cars", List.of("car", "cars"));
        token3.setOffset(16);

        List<Token> tokens = List.of(token1, token2, token3);

        compareAnnotations(text, tokens, new AnnotatorConfig().setStemMode("ALL").setLowercase(true));
    }

    @Test
    public void testMultiStemming() {
        String text = "make peace between wars";
        SimpleToken token1 = SimpleToken.fromStems("make", List.of("make"));
        SimpleToken token2 = SimpleToken.fromStems("peace", List.of("peace"));
        SimpleToken token3 = SimpleToken.fromStems("between", List.of("between"));
        SimpleToken token4 = SimpleToken.fromStems("wars", List.of("war"));
        token2.setOffset(5);
        token3.setOffset(11);
        token4.setOffset(19);
        List<Token> tokens = List.of(token1, token2, token3, token4);
        compareAnnotations(text, tokens, new AnnotatorConfig().setStemMode("ALL").setLowercase(true));
    }

    // --------------------------------------------------------------------------------
    // Utilities

    /**
     * Compares annotations produced by simple and full paths, ensuring they are equivalent.
     * Now directly calls the package-private annotateSimple() and annotateFull() methods.
     */
    private void compareAnnotations(String text, List<Token> tokens, AnnotatorConfig config) {
        Linguistics linguistics = newLinguistics(tokens);
        LinguisticsAnnotator annotator = new LinguisticsAnnotator(linguistics, config);
        TermOccurrences termOccurrences = new TermOccurrences(config.getMaxTermOccurrences());

        // Annotate with simple path
        StringFieldValue simpleValue = new StringFieldValue(text);
        SimpleIndexingAnnotations simple = new SimpleIndexingAnnotations();
        boolean simpleResult = annotator.annotateSimple(simple, text, tokens, termOccurrences);

        // Annotate with full path (need fresh token iterator)
        StringFieldValue fullValue = new StringFieldValue(text);
        TermOccurrences termOccurrences2 = new TermOccurrences(config.getMaxTermOccurrences());
        boolean fullResult = annotator.annotateFull(fullValue, newLinguistics(tokens).getTokenizer().tokenize(text, config.asLinguisticsParameters()), termOccurrences2);

        // Both should return the same result
        assertEquals("Simple and full paths should both succeed or both fail", simpleResult, fullResult);

        if (simpleResult && fullResult) {
            // Compare the annotations
            SpanTree fullTree = fullValue.getSpanTree(SpanTrees.LINGUISTICS);
            assertNotNull("Full tree should exist when annotator returned true", fullTree);
            assertEquivalent(text, simple, fullTree);
        }
    }

    /**
     * Asserts that SimpleIndexingAnnotations and SpanTree are equivalent.
     */
    private void assertEquivalent(String text, SimpleIndexingAnnotations simple, SpanTree tree) {
        // Extract annotations from SpanTree
        List<AnnotationInfo> treeAnnotations = new ArrayList<>();
        for (Annotation annotation : tree) {
            if (annotation.getType() != AnnotationTypes.TERM) continue;
            Span span = (Span) annotation.getSpanNode();
            if (span == null) continue;

            String term;
            if (annotation.getFieldValue() instanceof StringFieldValue) {
                term = ((StringFieldValue) annotation.getFieldValue()).getString();
            } else {
                // No override, term equals substring
                term = text.substring(span.getFrom(), span.getTo());
            }

            treeAnnotations.add(new AnnotationInfo(span.getFrom(), span.getLength(), term));
        }

        // Extract annotations from SimpleIndexingAnnotations
        List<AnnotationInfo> simpleAnnotations = new ArrayList<>();
        for (int i = 0; i < simple.getCount(); i++) {
            int from = simple.getFrom(i);
            int length = simple.getLength(i);
            String term = simple.getTerm(i);
            if (term == null) {
                term = text.substring(from, from + length);
            }
            simpleAnnotations.add(new AnnotationInfo(from, length, term));
        }

        // Sort both lists for comparison
        treeAnnotations.sort((a, b) -> {
            int cmp = Integer.compare(a.from, b.from);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(a.length, b.length);
            if (cmp != 0) return cmp;
            return a.term.compareTo(b.term);
        });
        simpleAnnotations.sort((a, b) -> {
            int cmp = Integer.compare(a.from, b.from);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(a.length, b.length);
            if (cmp != 0) return cmp;
            return a.term.compareTo(b.term);
        });

        // Compare counts
        assertEquals("Annotation count mismatch for text: " + text,
                     treeAnnotations.size(), simpleAnnotations.size());

        // Compare each annotation
        for (int i = 0; i < treeAnnotations.size(); i++) {
            AnnotationInfo expected = treeAnnotations.get(i);
            AnnotationInfo actual = simpleAnnotations.get(i);

            assertEquals("Annotation[" + i + "] from mismatch for text: " + text,
                         expected.from, actual.from);
            assertEquals("Annotation[" + i + "] length mismatch for text: " + text,
                         expected.length, actual.length);
            assertEquals("Annotation[" + i + "] term mismatch for text: " + text,
                         expected.term, actual.term);
        }
    }

    private static class AnnotationInfo {
        final int from;
        final int length;
        final String term;

        AnnotationInfo(int from, int length, String term) {
            this.from = from;
            this.length = length;
            this.term = term;
        }

        @Override
        public String toString() {
            return String.format("Annotation(from=%d, length=%d, term='%s')", from, length, term);
        }
    }

    private static Linguistics newLinguistics(List<Token> tokens) {
        Linguistics linguistics = Mockito.mock(Linguistics.class);
        Mockito.when(linguistics.getTokenizer()).thenReturn(new TestTokenizer(tokens));
        return linguistics;
    }

    private static class TestTokenizer implements Tokenizer {
        final List<Token> tokens;

        TestTokenizer(List<Token> tokens) {
            this.tokens = tokens;
        }

        @Override
        public Iterable<Token> tokenize(String input, LinguisticsParameters parameters) {
            return tokens;
        }
    }
}

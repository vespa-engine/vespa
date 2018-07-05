// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.linguistics;

import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationTypes;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenType;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.language.simple.SimpleToken;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class LinguisticsAnnotatorTestCase {

    private static final AnnotatorConfig CONFIG = new AnnotatorConfig();

    // --------------------------------------------------------------------------------
    //
    // Tests
    //
    // --------------------------------------------------------------------------------

    @Test
    public void requireThatAnnotateFailsWithZeroTokens() {
        assertAnnotations(null, "foo");
    }

    @Test
    public void requireThatAnnotateFailsWithoutIndexableTokenString() {
        for (TokenType type : TokenType.values()) {
            if (type.isIndexable()) {
                continue;
            }
            assertAnnotations(null, "foo", newToken("foo", "bar", type));
        }
    }

    @Test
    public void requireThatIndexableTokenStringsAreAnnotated() {
        SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        expected.spanList().span(0, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("bar")));
        for (TokenType type : TokenType.values()) {
            if (!type.isIndexable()) {
                continue;
            }
            assertAnnotations(expected, "foo", newToken("foo", "bar", type));
        }
    }

    @Test
    public void requireThatSpecialTokenStringsAreAnnotatedRegardlessOfType() {
        SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        expected.spanList().span(0, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("bar")));
        for (TokenType type : TokenType.values()) {
            assertAnnotations(expected, "foo", newToken("foo", "bar", type, true));
        }
    }

    @Test
    public void requireThatTermAnnotationsAreEmptyIfOrigIsLowerCase() {
        SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        expected.spanList().span(0, 3).annotate(new Annotation(AnnotationTypes.TERM));
        for (boolean specialToken : Arrays.asList(true, false)) {
            for (TokenType type : TokenType.values()) {
                if (!specialToken && !type.isIndexable()) {
                    continue;
                }
                assertAnnotations(expected, "foo", newToken("foo", "foo", type, specialToken));
            }
        }
    }

    @Test
    public void requireThatTermAnnotationsAreLowerCased() {
        SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        expected.spanList().span(0, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("bar")));
        for (boolean specialToken : Arrays.asList(true, false)) {
            for (TokenType type : TokenType.values()) {
                if (!specialToken && !type.isIndexable()) {
                    continue;
                }
                assertAnnotations(expected, "foo", newToken("foo", "BAR", type, specialToken));
            }
        }
    }

    @Test
    public void requireThatCompositeTokensAreFlattened() {
        SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        expected.spanList().span(0, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("foo")));
        expected.spanList().span(3, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("bar")));
        expected.spanList().span(6, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("baz")));

        SimpleToken token = newToken("FOOBARBAZ", "foobarbaz", TokenType.ALPHABETIC)
                .addComponent(newToken("FOO", "foo", TokenType.ALPHABETIC).setOffset(0))
                .addComponent(newToken("BARBAZ", "barbaz", TokenType.ALPHABETIC).setOffset(3)
                                      .addComponent(newToken("BAR", "bar", TokenType.ALPHABETIC).setOffset(3))
                                      .addComponent(newToken("BAZ", "baz", TokenType.ALPHABETIC).setOffset(6)));
        assertAnnotations(expected, "foobarbaz", token);
    }

    @Test
    public void requireThatCompositeSpecialTokensAreNotFlattened() {
        SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        expected.spanList().span(0, 9).annotate(new Annotation(AnnotationTypes.TERM,
                                                               new StringFieldValue("foobarbaz")));

        SimpleToken token = newToken("FOOBARBAZ", "foobarbaz", TokenType.ALPHABETIC).setSpecialToken(true)
                .addComponent(newToken("FOO", "foo", TokenType.ALPHABETIC).setOffset(0))
                .addComponent(newToken("BARBAZ", "barbaz", TokenType.ALPHABETIC).setOffset(3)
                                      .addComponent(newToken("BAR", "bar", TokenType.ALPHABETIC).setOffset(3))
                                      .addComponent(newToken("BAZ", "baz", TokenType.ALPHABETIC).setOffset(6)));
        assertAnnotations(expected, "foobarbaz", token);
    }

    @Test
    public void requireThatErrorTokensAreSkipped() {
        assertAnnotations(null, "foo", new SimpleToken("foo").setType(TokenType.ALPHABETIC)
                                                             .setOffset(-1));
    }

    @Test
    public void requireThatTermReplacementsAreApplied() {
        SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        expected.spanList().span(0, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("bar")));
        for (boolean specialToken : Arrays.asList(true, false)) {
            for (TokenType type : TokenType.values()) {
                if (!specialToken && !type.isIndexable()) {
                    continue;
                }
                assertAnnotations(expected, "foo",
                                  newLinguistics(Arrays.asList(newToken("foo", "foo", type, specialToken)),
                                                 Collections.singletonMap("foo", "bar")));
            }
        }
    }

    @Test
    public void requireThatExistingAnnotationsAreKept() {
        SpanTree spanTree = new SpanTree(SpanTrees.LINGUISTICS);
        spanTree.spanList().span(0, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("baz")));

        StringFieldValue val = new StringFieldValue("foo");
        val.setSpanTree(spanTree);

        Linguistics linguistics = newLinguistics(Arrays.asList(newToken("foo", "bar", TokenType.ALPHABETIC, false)),
                                                 Collections.<String, String>emptyMap());
        new LinguisticsAnnotator(linguistics, CONFIG).annotate(val);

        assertTrue(new LinguisticsAnnotator(linguistics, CONFIG).annotate(val));
        assertEquals(spanTree, val.getSpanTree(SpanTrees.LINGUISTICS));
    }

    @Test
    public void requireThatTokenizeCappingWorks() {
        String shortString = "short string";
        SpanTree spanTree = new SpanTree(SpanTrees.LINGUISTICS);
        spanTree.setStringFieldValue(new StringFieldValue(shortString));
        spanTree.spanList().span(0, 5).annotate(new Annotation(AnnotationTypes.TERM));
        spanTree.spanList().span(6, 6).annotate(new Annotation(AnnotationTypes.TERM));

        StringFieldValue shortValue = new StringFieldValue(shortString);

        Linguistics linguistics = new SimpleLinguistics();

        LinguisticsAnnotator annotator = new LinguisticsAnnotator(linguistics, new AnnotatorConfig().setMaxTokenLength(12));

        assertTrue(annotator.annotate(shortValue));
        assertEquals(spanTree, shortValue.getSpanTree(SpanTrees.LINGUISTICS));
        assertEquals(shortString, shortValue.getSpanTree(SpanTrees.LINGUISTICS).getStringFieldValue().getString());

        StringFieldValue cappedValue = new StringFieldValue(shortString + " a longer string");
        assertTrue(annotator.annotate(cappedValue));
        assertEquals((shortString + " a longer string"), cappedValue.getSpanTree(SpanTrees.LINGUISTICS).getStringFieldValue().getString());
    }

    @Test
    public void requireThatMaxTermOccurencesIsHonored() {
        final String inputTerm = "foo";
        final String stemmedInputTerm = "bar"; // completely different from
                                               // inputTerm for safer test
        final String paddedInputTerm = inputTerm + " ";
        final SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        final int inputTermOccurence = AnnotatorConfig.DEFAULT_MAX_TERM_OCCURRENCES * 2;
        for (int i = 0; i < AnnotatorConfig.DEFAULT_MAX_TERM_OCCURRENCES; ++i) {
            expected.spanList().span(i * paddedInputTerm.length(), inputTerm.length())
                    .annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue(stemmedInputTerm)));
        }
        for (TokenType type : TokenType.values()) {
            if (!type.isIndexable()) {
                continue;
            }
            StringBuilder input = new StringBuilder();
            Token[] tokens = new Token[inputTermOccurence];
            for (int i = 0; i < inputTermOccurence; ++i) {
                SimpleToken t = newToken(inputTerm, stemmedInputTerm, type);
                t.setOffset(i * paddedInputTerm.length());
                tokens[i] = t;
                input.append(paddedInputTerm);
            }
            assertAnnotations(expected, input.toString(), tokens);
        }
    }

    // --------------------------------------------------------------------------------
    //
    // Utilities
    //
    // --------------------------------------------------------------------------------

    private static SimpleToken newToken(String orig, String stem, TokenType type) {
        return newToken(orig, stem, type, false);
    }

    private static SimpleToken newToken(String orig, String stem, TokenType type, boolean specialToken) {
        return new SimpleToken(orig).setTokenString(stem)
                                    .setType(type)
                                    .setSpecialToken(specialToken);
    }

    private static void assertAnnotations(SpanTree expected, String value, Token... tokens) {
        assertAnnotations(expected, value, newLinguistics(Arrays.asList(tokens), Collections.<String, String>emptyMap()));
    }

    private static void assertAnnotations(SpanTree expected, String str, Linguistics linguistics) {
        StringFieldValue val = new StringFieldValue(str);
        assertEquals(expected != null, new LinguisticsAnnotator(linguistics, CONFIG).annotate(val));
        assertEquals(expected, val.getSpanTree(SpanTrees.LINGUISTICS));
    }

    private static Linguistics newLinguistics(List<? extends Token> tokens, Map<String, String> replacementTerms) {
        Linguistics linguistics = Mockito.mock(Linguistics.class);
        Mockito.when(linguistics.getTokenizer()).thenReturn(new MyTokenizer(tokens, replacementTerms));
        return linguistics;
    }

    private static class MyTokenizer implements Tokenizer {

        final List<Token> tokens;
        final Map<String, String> replacementTerms;

        public MyTokenizer(List<? extends Token> tokens, Map<String, String> replacementTerms) {
            this.tokens = new ArrayList<>(tokens);
            this.replacementTerms = replacementTerms;
        }

        @Override
        public Iterable<Token> tokenize(String input, Language language, StemMode stemMode, boolean removeAccents) {
            return tokens;
        }

        @Override
        public String getReplacementTerm(String term) {
            String replacement = replacementTerms.get(term);
            return replacement != null ? replacement : term;
        }
    }
}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
            assertAnnotations(null, "foo", token("foo", "bar", type));
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
            assertAnnotations(expected, "foo", token("foo", "bar", type));
        }
    }

    @Test
    public void requireThatIndexableTokenStringsAreAnnotatedWithModeALL() {
        SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        var span1 = expected.spanList().span(0, 6);
        span1.annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("tesla")));
        span1.annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("teslas")));
        var span2 = expected.spanList().span(0, 4);
        span2.annotate(new Annotation(AnnotationTypes.TERM));
        span2.annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("car")));
        var span3 = expected.spanList().span(0, 8);
        span3.annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("modelxes")));
        span3.annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("modelx")));
        span3.annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("mex")));
        for (TokenType type : TokenType.values()) {
            if (!type.isIndexable()) continue;
            assertAnnotations(expected, "Tesla cars", new AnnotatorConfig().setStemMode("ALL"),
                              token("Teslas", "tesla", type),
                              token("cars", "car", type),
                              SimpleToken.fromStems("ModelXes", List.of("modelxes", "modelx", "mex")));
        }
    }

    @Test
    public void requireThatSpecialTokenStringsAreAnnotatedRegardlessOfType() {
        SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        expected.spanList().span(0, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("bar")));
        for (TokenType type : TokenType.values()) {
            assertAnnotations(expected, "foo", token("foo", "bar", type, true));
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
                assertAnnotations(expected, "foo", token("foo", "foo", type, specialToken));
            }
        }
    }

    @Test
    public void requireThatTermAnnotationsPreserveCasing() {
        SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        expected.spanList().span(0, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("BaR")));
        for (boolean specialToken : Arrays.asList(true, false)) {
            for (TokenType type : TokenType.values()) {
                if (!specialToken && !type.isIndexable()) {
                    continue;
                }
                assertAnnotations(expected, "foo", token("foo", "BaR", type, specialToken));
            }
        }
    }

    @Test
    public void requireThatCompositeTokensAreFlattened() {
        SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        expected.spanList().span(0, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("foo")));
        expected.spanList().span(3, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("bar")));
        expected.spanList().span(6, 3).annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("baz")));

        SimpleToken token = token("FOOBARBAZ", "foobarbaz", TokenType.ALPHABETIC)
                .addComponent(token("FOO", "foo", TokenType.ALPHABETIC).setOffset(0))
                .addComponent(token("BARBAZ", "barbaz", TokenType.ALPHABETIC).setOffset(3)
                                                                             .addComponent(token("BAR", "bar", TokenType.ALPHABETIC).setOffset(3))
                                                                             .addComponent(token("BAZ", "baz", TokenType.ALPHABETIC).setOffset(6)));
        assertAnnotations(expected, "foobarbaz", token);
    }

    @Test
    public void requireThatCompositeSpecialTokensAreNotFlattened() {
        SpanTree expected = new SpanTree(SpanTrees.LINGUISTICS);
        expected.spanList().span(0, 9).annotate(new Annotation(AnnotationTypes.TERM,
                                                               new StringFieldValue("foobarbaz")));

        SimpleToken token = token("FOOBARBAZ", "foobarbaz", TokenType.ALPHABETIC).setSpecialToken(true)
                                                                                 .addComponent(token("FOO", "foo", TokenType.ALPHABETIC).setOffset(0))
                                                                                 .addComponent(token("BARBAZ", "barbaz", TokenType.ALPHABETIC).setOffset(3)
                                                                                                                                              .addComponent(token("BAR", "bar", TokenType.ALPHABETIC).setOffset(3))
                                                                                                                                              .addComponent(token("BAZ", "baz", TokenType.ALPHABETIC).setOffset(6)));
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
                                  new AnnotatorConfig(),
                                  newLinguistics(List.of(token("foo", "foo", type, specialToken)),
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

        Linguistics linguistics = newLinguistics(List.of(token("foo", "bar", TokenType.ALPHABETIC, false)),
                                                 Collections.<String, String>emptyMap());
        assertTrue(new LinguisticsAnnotator(linguistics, new AnnotatorConfig()).annotate(val));
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
    public void requireThatMaxTermOccurrencesIsHonored() {
        final String inputTerm = "foo";
        final String stemmedInputTerm = "bar"; // completely different from inputTerm for safer test
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
                SimpleToken t = token(inputTerm, stemmedInputTerm, type);
                t.setOffset(i * paddedInputTerm.length());
                tokens[i] = t;
                input.append(paddedInputTerm);
            }
            assertAnnotations(expected, input.toString(), tokens);
        }
    }

    // --------------------------------------------------------------------------------
    // Utilities

    private static SimpleToken token(String orig, String stem, TokenType type) {
        return token(orig, stem, type, false);
    }

    private static SimpleToken token(String orig, String stem, TokenType type, boolean specialToken) {
        return new SimpleToken(orig).setTokenString(stem)
                                    .setType(type)
                                    .setSpecialToken(specialToken);
    }

    private static void assertAnnotations(SpanTree expected, String value, Token... tokens) {
        assertAnnotations(expected, value, new AnnotatorConfig(), newLinguistics(Arrays.asList(tokens), Collections.emptyMap()));
    }

    private static void assertAnnotations(SpanTree expected, String value, AnnotatorConfig config, Token... tokens) {
        assertAnnotations(expected, value, config, newLinguistics(Arrays.asList(tokens), Collections.emptyMap()));
    }

    private static void assertAnnotations(SpanTree expected, String str, AnnotatorConfig config, Linguistics linguistics) {
        StringFieldValue val = new StringFieldValue(str);
        assertEquals(expected != null, new LinguisticsAnnotator(linguistics, config).annotate(val));
        assertEquals(expected, val.getSpanTree(SpanTrees.LINGUISTICS));
    }

    private static Linguistics newLinguistics(List<? extends Token> tokens, Map<String, String> replacementTerms) {
        Linguistics linguistics = Mockito.mock(Linguistics.class);
        Mockito.when(linguistics.getTokenizer()).thenReturn(new MyTokenizer(tokens, replacementTerms));
        return linguistics;
    }

    private static class MyTokenizer implements Tokenizer {

        final List<Token> tokens;

        public MyTokenizer(List<? extends Token> tokens, Map<String, String> replacementTerms) {
            this.tokens = tokens.stream().map(token -> replace(token, replacementTerms)).toList();
        }

        private Token replace(Token token, Map<String, String> replacementTerms) {
            var simpleToken = (SimpleToken)token;
            simpleToken.setTokenString(replacementTerms.getOrDefault(token.getTokenString(), token.getTokenString()));
            return simpleToken;
        }

        @Override
        public Iterable<Token> tokenize(String input, Language language, StemMode stemMode, boolean removeAccents) {
            return tokens;
        }

    }

}

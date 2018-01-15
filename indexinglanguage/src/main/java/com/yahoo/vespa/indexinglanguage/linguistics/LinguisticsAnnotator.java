// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.linguistics;

import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationTypes;
import com.yahoo.document.annotation.Span;
import com.yahoo.document.annotation.SpanList;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.Tokenizer;

import java.util.HashMap;
import java.util.Map;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * This is a tool for adding {@link AnnotationTypes} type annotations to {@link StringFieldValue} objects.
 *
 * @author Simon Thoresen
 */
public class LinguisticsAnnotator {

    private final Linguistics factory;
    private final AnnotatorConfig config;

    private static class TermOccurrences {
        final Map<String, Integer> termOccurrences = new HashMap<>();
        final int maxOccurrences;

        public TermOccurrences(int maxOccurences) {
            this.maxOccurrences = maxOccurences;
        }

        boolean termCountBelowLimit(String term) {
            String lowerCasedTerm = toLowerCase(term);
            int occurences = termOccurrences.getOrDefault(lowerCasedTerm, 0);
            if (occurences >= maxOccurrences) {
                return false;
            }

            termOccurrences.put(lowerCasedTerm, occurences + 1);
            return true;
        }
    }

    /**
     * Constructs a new instance of this annotator.
     *
     * @param factory the linguistics factory to use when annotating
     * @param config  the linguistics config to use
     */
    public LinguisticsAnnotator(Linguistics factory, AnnotatorConfig config) {
        this.factory = factory;
        this.config = config;
    }

    /**
     * Annotates the given string with the appropriate linguistics annotations.
     *
     * @param text the text to annotate
     * @return whether or not anything was annotated
     */
    public boolean annotate(StringFieldValue text) {
        if (text.getSpanTree(SpanTrees.LINGUISTICS) != null) return true;  // Already annotated with LINGUISTICS.

        Tokenizer tokenizer = factory.getTokenizer();
        String input = (text.getString().length() <=  config.getMaxTokenizeLength())
                ? text.getString()
                : text.getString().substring(0, config.getMaxTokenizeLength());
        Iterable<Token> tokens = tokenizer.tokenize(input, config.getLanguage(), config.getStemMode(),
                                                    config.getRemoveAccents());
        TermOccurrences termOccurrences = new TermOccurrences(config.getMaxTermOccurrences());
        SpanTree tree = new SpanTree(SpanTrees.LINGUISTICS);
        for (Token token : tokens) {
            addAnnotationSpan(text.getString(), tree.spanList(), tokenizer, token, config.getStemMode(), termOccurrences);
        }

        if (tree.numAnnotations() == 0) return false;
        text.setSpanTree(tree);
        return true;
    }

    /**
     * Creates a TERM annotation which has the lowercase value as annotation (only) if it is different from the
     * original.
     *
     * @param termToLowerCase The term to lower case.
     * @param origTerm        The original term.
     * @return the created TERM annotation.
     */
    public static Annotation lowerCaseTermAnnotation(String termToLowerCase, String origTerm) {
        String annotationValue = toLowerCase(termToLowerCase);
        if (annotationValue.equals(origTerm)) {
            return new Annotation(AnnotationTypes.TERM);
        }
        return new Annotation(AnnotationTypes.TERM, new StringFieldValue(annotationValue));
    }

    private static void addAnnotation(Span here, String term, String orig, TermOccurrences termOccurrences) {
        if (termOccurrences.termCountBelowLimit(term)) {
            here.annotate(lowerCaseTermAnnotation(term, orig));
        }
    }

    private static void addAnnotationSpan(String input, SpanList parent, Tokenizer tokenizer, Token token, StemMode mode, TermOccurrences termOccurrences) {
        if ( ! token.isSpecialToken()) {
            if (token.getNumComponents() > 0) {
                for (int i = 0; i < token.getNumComponents(); ++i) {
                    addAnnotationSpan(input, parent, tokenizer, token.getComponent(i), mode, termOccurrences);
                }
                return;
            }
            if ( ! token.isIndexable()) {
                return;
            }
        }
        String orig = token.getOrig();
        int pos = (int)token.getOffset();
        if (pos >= input.length()) {
            throw new IllegalArgumentException("Token '" + orig + "' has offset " + pos + ", which is outside the " +
                                               "bounds of the input string; " + input);
        }
        int len = orig.length();
        if (pos + len > input.length()) {
            throw new IllegalArgumentException("Token '" + orig + "' has offset " + pos + ", which makes it overflow " +
                                               "the bounds of the input string; " + input);
        }
        if (mode == StemMode.ALL) {
            Span where = parent.span(pos, len);
            String lowercasedOrig = toLowerCase(orig);
            addAnnotation(where, orig, orig, termOccurrences);

            String lowercasedTerm = lowercasedOrig;
            String term = token.getTokenString();
            if (term != null) {
                term = tokenizer.getReplacementTerm(term);
            }
            if (term != null) {
                lowercasedTerm = toLowerCase(term);
            }
            if (! lowercasedOrig.equals(lowercasedTerm)) {
                addAnnotation(where, term, orig, termOccurrences);
            }
            for (int i = 0; i < token.getNumStems(); i++) {
                String stem = token.getStem(i);
                String lowercasedStem = toLowerCase(stem);
                if (! (lowercasedOrig.equals(lowercasedStem) || lowercasedTerm.equals(lowercasedStem))) {
                    addAnnotation(where, stem, orig, termOccurrences);
                }
            }
        } else {
            String term = token.getTokenString();
            if (term != null) {
                term = tokenizer.getReplacementTerm(term);
            }
            if (term == null || term.trim().isEmpty()) {
                return;
            }
            if (termOccurrences.termCountBelowLimit(term))  {
                parent.span(pos, len).annotate(lowerCaseTermAnnotation(term, token.getOrig()));
            }
        }
    }

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import ai.vespa.opennlp.OpenNlpConfig;
import com.yahoo.language.Language;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.Normalizer;
import com.yahoo.language.process.Segmenter;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Stemmer;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.Tokenizer;

import java.util.List;

import static com.yahoo.language.LinguisticsCase.toLowerCase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class OpenNlpLinguisticsTester {

    private final Tokenizer tokenizer;
    private final Segmenter segmenter;
    private final Stemmer stemmer;
    private final Normalizer normalizer;

    public OpenNlpLinguisticsTester() {
        this(new OpenNlpLinguistics());
    }

    public OpenNlpLinguisticsTester(OpenNlpConfig config) {
        this(new OpenNlpLinguistics(config));
    }

    public OpenNlpLinguisticsTester(OpenNlpLinguistics linguistics) {
        this.tokenizer = linguistics.getTokenizer();
        this.segmenter = linguistics.getSegmenter();
        this.stemmer = linguistics.getStemmer();
        this.normalizer = linguistics.getNormalizer();
    }

    Tokenizer tokenizer() { return tokenizer; }
    Segmenter segmenter() { return segmenter; }
    Stemmer stemmer() { return stemmer; }
    Normalizer normalizer() { return normalizer; }

    Iterable<Token> tokenize(String input, Language language) {
        var parameters = new LinguisticsParameters(language, StemMode.SHORTEST, true, true);
        return tokenizer.tokenize(input, parameters);
    }

    String tokenizeToString(String input, Language language) {
        return tokenize(input, language).iterator().next().getTokenString();
    }

    String stemAndNormalize(String input, Language language) {
        var parameters = new LinguisticsParameters(language, StemMode.SHORTEST, true, true);
        var stemListList = stemmer.stem(input, parameters);
        return normalizer.normalize(stemListList.get(0).get(0));
    }

    void recurseDecompose(Token t) {
        assertTrue(t.getOffset() >= 0);
        assertTrue(t.getOrig().length() >= 0);

        int numComp = t.getNumComponents();
        for (int i = 0; i < numComp; i++) {
            Token comp = t.getComponent(i);
            recurseDecompose(comp);
        }
    }

    boolean assertMonoIncr(Iterable<Long> n) {
        long trailing = -1;
        for (long i : n) {
            if (i < trailing) {
                return false;
            }
            trailing = i;
        }
        return true;
    }

    void assertTokenize(String input, List<String> indexed, List<String> orig) {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.NONE, false, true);
        assertTokenize(input, parameters, indexed, orig);
    }

    /**
     * Compare the results of running an input string through the tokenizer with an "index" truth, and an optional
     * "orig" truth.
     *
     * @param input      the text to process, passed to tokenizer
     * @param indexed    compared to the "TokenString" result from the tokenizer
     * @param orig       compared to the "Orig" result from the tokenizer
     */
    void assertTokenize(String input, LinguisticsParameters parameters,
                        List<String> indexed, List<String> orig) {
        int i = 0;
        int j = 0;
        for (Token token : tokenizer.tokenize(input, parameters)) {
            // System.err.println("got token orig '"+token.getOrig()+"'");
            // System.err.println("got token stem '"+token.getTokenString(stemMode)+"'");
            if (token.getNumComponents() > 0) {
                for (int comp = 0; comp < token.getNumComponents(); comp++) {
                    Token t = token.getComponent(comp);
                    if (t.getType().isIndexable()) {
                        assertEquals("comp index: " + i, indexed.get(i++),
                                     parameters.lowercase() ? toLowerCase(t.getTokenString()) : t.getTokenString());
                    }
                }
            } else {
                if (token.getType().isIndexable()) {
                    assertEquals("exp index: " + i, indexed.get(i++),
                                 parameters.lowercase() ? toLowerCase(token.getTokenString()) : token.getTokenString());
                }
            }
            if (orig != null) {
                assertEquals("orig index: " + j, orig.get(j++), token.getOrig());
            }
        }
        assertEquals("indexed length", indexed.size(), i);
        if (orig != null) {
            assertEquals("orig length", orig.size(), j);
        }
    }

}

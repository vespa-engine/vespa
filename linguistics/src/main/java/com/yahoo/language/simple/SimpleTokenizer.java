// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.Language;
import com.yahoo.language.LinguisticsCase;
import com.yahoo.language.process.*;
import com.yahoo.language.simple.kstem.KStemmer;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>A tokenizer which splits on whitespace, normalizes and transforms using the given implementations
 * and stems using the kstem algorithm.</p>
 *
 * <p>This is not multithread safe.</p>
 *
 * @author Mathias Mølster Lidal
 * @author bratseth
 */
public class SimpleTokenizer implements Tokenizer {

    private final static int SPACE_CODE = 32;
    private final Normalizer normalizer;
    private final Transformer transformer;

    public SimpleTokenizer() {
        this(new SimpleNormalizer(), new SimpleTransformer());
    }

    public SimpleTokenizer(Normalizer normalizer) {
        this(normalizer, new SimpleTransformer());
    }

    public SimpleTokenizer(Normalizer normalizer, Transformer transformer) {
        this.normalizer = normalizer;
        this.transformer = transformer;
    }

    @Override
    public Iterable<Token> tokenize(String input, Language language, StemMode stemMode, boolean removeAccents) {
        if (input.isEmpty()) return Collections.emptyList();

        opennlp.tools.stemmer.Stemmer stemmer = getStemmerForLanguage(language);

        List<Token> tokens = new ArrayList<>();
        int nextCode = input.codePointAt(0);
        TokenType prevType = SimpleTokenType.valueOf(nextCode);
        for (int prev = 0, next = Character.charCount(nextCode); next <= input.length(); ) {
            nextCode = next < input.length() ? input.codePointAt(next) : SPACE_CODE;
            TokenType nextType = SimpleTokenType.valueOf(nextCode);
            if (!prevType.isIndexable() || !nextType.isIndexable()) {
                String original = input.substring(prev, next);
                String token = processToken(original, language, stemMode, removeAccents, stemmer);
                tokens.add(new SimpleToken(original).setOffset(prev)
                        .setType(prevType)
                        .setTokenString(token));
                prev = next;
                prevType = nextType;
            }
            next += Character.charCount(nextCode);
        }
        return tokens;
    }

    private String processToken(String token, Language language, StemMode stemMode, boolean removeAccents, Stemmer stemmer) {
        token = normalizer.normalize(token);
        token = LinguisticsCase.toLowerCase(token);
        if (removeAccents)
            token = transformer.accentDrop(token, language);
        if (stemMode != StemMode.NONE && token != null)
            token = stemmer.stem(token).toString();
        return token;
    }

    private static Stemmer getStemmerForLanguage(Language language) {
        Stemmer stemmer = charSequence -> charSequence == null ? null : new KStemmer().stem(charSequence.toString());
        if (language == null) {
            return stemmer;
        }
        SnowballStemmer.ALGORITHM alg;
        switch (language) {
            case DANISH:
                alg = SnowballStemmer.ALGORITHM.DANISH;
                break;
            case DUTCH:
                alg = SnowballStemmer.ALGORITHM.DUTCH;
                break;
            case FINNISH:
                alg = SnowballStemmer.ALGORITHM.FINNISH;
                break;
            case FRENCH:
                alg = SnowballStemmer.ALGORITHM.FRENCH;
                break;
            case GERMAN:
                alg = SnowballStemmer.ALGORITHM.GERMAN;
                break;
            case HUNGARIAN:
                alg = SnowballStemmer.ALGORITHM.HUNGARIAN;
                break;
            case IRISH:
                alg = SnowballStemmer.ALGORITHM.IRISH;
                break;
            case ITALIAN:
                alg = SnowballStemmer.ALGORITHM.ITALIAN;
                break;
            case NORWEGIAN_BOKMAL:
            case NORWEGIAN_NYNORSK:
                alg = SnowballStemmer.ALGORITHM.NORWEGIAN;
                break;
            case PORTUGUESE:
                alg = SnowballStemmer.ALGORITHM.PORTUGUESE;
                break;
            case ROMANIAN:
                alg = SnowballStemmer.ALGORITHM.ROMANIAN;
                break;
            case RUSSIAN:
                alg = SnowballStemmer.ALGORITHM.RUSSIAN;
                break;
            case SPANISH:
                alg = SnowballStemmer.ALGORITHM.SPANISH;
                break;
            case SWEDISH:
                alg = SnowballStemmer.ALGORITHM.SWEDISH;
                break;
            case TURKISH:
                alg = SnowballStemmer.ALGORITHM.TURKISH;
                break;
            default:
                return stemmer;
        }
        return new SnowballStemmer(alg);
    }
}

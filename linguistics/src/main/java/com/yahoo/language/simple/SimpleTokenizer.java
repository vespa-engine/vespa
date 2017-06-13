// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.Language;
import com.yahoo.language.LinguisticsCase;
import com.yahoo.language.process.*;
import com.yahoo.language.simple.kstem.KStemmer;

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
    private final KStemmer stemmer = new KStemmer();

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

        List<Token> tokens = new ArrayList<>();
        int nextCode = input.codePointAt(0);
        TokenType prevType = SimpleTokenType.valueOf(nextCode);
        for (int prev = 0, next = Character.charCount(nextCode); next <= input.length(); ) {
            nextCode = next < input.length() ? input.codePointAt(next) : SPACE_CODE;
            TokenType nextType = SimpleTokenType.valueOf(nextCode);
            if (!prevType.isIndexable() || !nextType.isIndexable()) {
                String original = input.substring(prev, next);
                String token = processToken(original, language, stemMode, removeAccents);
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

    private String processToken(String token, Language language, StemMode stemMode, boolean removeAccents) {
        token = normalizer.normalize(token);
        token = LinguisticsCase.toLowerCase(token);
        if (removeAccents)
            token = transformer.accentDrop(token, language);
        if (stemMode != StemMode.NONE)
            token = stemmer.stem(token);
        return token;
    }

}

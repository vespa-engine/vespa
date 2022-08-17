// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import com.yahoo.language.Language;
import com.yahoo.language.LinguisticsCase;
import com.yahoo.language.process.Normalizer;
import com.yahoo.language.process.SpecialTokenRegistry;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.process.Transformer;
import com.yahoo.language.simple.SimpleNormalizer;
import com.yahoo.language.simple.SimpleTokenizer;
import com.yahoo.language.simple.SimpleTransformer;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmer;

import java.util.List;

/**
 * Tokenizer using OpenNlp
 *
 * @author matskin
 * @author bratseth
 */
public class OpenNlpTokenizer implements Tokenizer {

    private final static int SPACE_CODE = 32;
    private final Normalizer normalizer;
    private final Transformer transformer;
    private final SimpleTokenizer simpleTokenizer;
    private final SpecialTokenRegistry specialTokenRegistry;

    public OpenNlpTokenizer() {
        this(new SimpleNormalizer(), new SimpleTransformer());
    }

    public OpenNlpTokenizer(Normalizer normalizer, Transformer transformer) {
        this(normalizer, transformer, new SpecialTokenRegistry(List.of()));
    }

    public OpenNlpTokenizer(Normalizer normalizer, Transformer transformer, SpecialTokenRegistry specialTokenRegistry) {
        this.normalizer = normalizer;
        this.transformer = transformer;
        this.specialTokenRegistry = specialTokenRegistry;
        this.simpleTokenizer = new SimpleTokenizer(normalizer, transformer, specialTokenRegistry);
    }

    @Override
    public Iterable<Token> tokenize(String input, Language language, StemMode stemMode, boolean removeAccents) {
        Stemmer stemmer = stemmerFor(language, stemMode);
        if (stemmer == null)
            return simpleTokenizer.tokenize(input, language, stemMode, removeAccents);
        else
            return simpleTokenizer.tokenize(input, token -> processToken(token, language, stemMode, removeAccents, stemmer));
    }

    private String processToken(String token, Language language, StemMode stemMode, boolean removeAccents,
                                Stemmer stemmer) {
        token = normalizer.normalize(token);
        token = LinguisticsCase.toLowerCase(token);
        if (removeAccents)
            token = transformer.accentDrop(token, language);
        if (stemMode != StemMode.NONE)
            token = stemmer.stem(token).toString();
        return token;
    }

    private Stemmer stemmerFor(Language language, StemMode stemMode) {
        if (language == null || language == Language.ENGLISH || stemMode == StemMode.NONE) return null;
        SnowballStemmer.ALGORITHM algorithm = algorithmFor(language);
        if (algorithm == null) return null;
        return new SnowballStemmer(algorithm);
    }

    private SnowballStemmer.ALGORITHM algorithmFor(Language language) {
        switch (language) {
            case DANISH: return SnowballStemmer.ALGORITHM.DANISH;
            case DUTCH: return SnowballStemmer.ALGORITHM.DUTCH;
            case FINNISH: return SnowballStemmer.ALGORITHM.FINNISH;
            case FRENCH: return SnowballStemmer.ALGORITHM.FRENCH;
            case GERMAN: return SnowballStemmer.ALGORITHM.GERMAN;
            case HUNGARIAN: return SnowballStemmer.ALGORITHM.HUNGARIAN;
            case IRISH: return SnowballStemmer.ALGORITHM.IRISH;
            case ITALIAN: return SnowballStemmer.ALGORITHM.ITALIAN;
            case NORWEGIAN_BOKMAL: return SnowballStemmer.ALGORITHM.NORWEGIAN;
            case NORWEGIAN_NYNORSK: return SnowballStemmer.ALGORITHM.NORWEGIAN;
            case PORTUGUESE: return SnowballStemmer.ALGORITHM.PORTUGUESE;
            case ROMANIAN: return SnowballStemmer.ALGORITHM.ROMANIAN;
            case RUSSIAN: return SnowballStemmer.ALGORITHM.RUSSIAN;
            case SPANISH: return SnowballStemmer.ALGORITHM.SPANISH;
            case SWEDISH: return SnowballStemmer.ALGORITHM.SWEDISH;
            case TURKISH: return SnowballStemmer.ALGORITHM.TURKISH;
            case ENGLISH: return SnowballStemmer.ALGORITHM.ENGLISH;
            default: return null;
        }
    }

}

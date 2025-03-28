// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.lucene;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.FileReference;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.StemList;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author dainiusjocas
 */
public class LuceneTokenizerTest {

    @Test
    public void testTokenizer() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, true, true);
        String text = "This is my Text";
        Iterable<Token> tokens = luceneLinguistics().getTokenizer()
                .tokenize(text, parameters);
        assertEquals(List.of("my", "text"), tokenStrings(tokens));
    }

    @Test
    public void testLithuanianTokenizer() {
        var parameters = new LinguisticsParameters(Language.LITHUANIAN, StemMode.ALL, true, true);
        String text = "Žalgirio mūšio data yra 1410 metai";
        Iterable<Token> tokens = luceneLinguistics().getTokenizer().tokenize(text, parameters);
        assertEquals(List.of("žalgir", "mūš", "dat", "1410", "met"), tokenStrings(tokens));
    }

    @Test
    public void testStemming() {
        String text = "mūšio";
        var parameters = new LinguisticsParameters(Language.LITHUANIAN, StemMode.ALL, true, true);
        List<StemList> tokens = luceneLinguistics().getStemmer().stem(text, parameters);
        assertEquals(1, tokens.size());
        assertEquals("mūš", tokens.get(0).get(0));
    }

    private Linguistics luceneLinguistics() {
        return new LuceneLinguistics(
                new LuceneAnalysisConfig.Builder()
                        .configDir(Optional.of(FileReference
                                .mockFileReferenceForUnitTesting(new File("."))))
                        .build(),
                new ComponentRegistry<>());
    }

    private void assertToken(String tokenString, Iterator<Token> tokens) {
        Token t = tokens.next();
        assertEquals(tokenString, t.getTokenString());
    }

    private List<Token> iterableToList(Iterable<Token> tokens) {
        List<Token> tokenList = new ArrayList<>();
        tokens.forEach(tokenList::add);
        return tokenList;
    }

    private List<String> tokenStrings(Iterable<Token> tokens) {
        List<String> tokenList = new ArrayList<>();
        tokens.forEach(token -> {
            tokenList.add(token.getTokenString());
        });
        return tokenList;
    }

    @Test
    public void testAnalyzerConfiguration() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, false, true);
        String languageCode = Language.ENGLISH.languageCode();
        LuceneAnalysisConfig enConfig = new LuceneAnalysisConfig.Builder()
                .configDir(Optional.of(FileReference.mockFileReferenceForUnitTesting(new File("."))))
                .analysis(
                        Map.of(languageCode,
                                new LuceneAnalysisConfig
                                        .Analysis
                                        .Builder()
                                        .tokenFilters(List.of(
                                                new LuceneAnalysisConfig
                                                        .Analysis
                                                        .TokenFilters
                                                        .Builder()
                                                        .name("englishMinimalStem"),
                                                new LuceneAnalysisConfig
                                                        .Analysis
                                                        .TokenFilters
                                                        .Builder()
                                                        .name("uppercase"))))
                ).build();
        LuceneLinguistics linguistics = new LuceneLinguistics(enConfig, new ComponentRegistry<>());
        Iterable<Token> tokens = linguistics.getTokenizer().tokenize("Dogs and cats", parameters);
        assertEquals(List.of("DOG", "AND", "CAT"), tokenStrings(tokens));
    }

    @Test
    public void testEnglishStemmerAnalyzerConfiguration() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, false, true);
        String languageCode = Language.ENGLISH.languageCode();
        LuceneAnalysisConfig enConfig = new LuceneAnalysisConfig.Builder()
                .configDir(Optional.of(FileReference.mockFileReferenceForUnitTesting(new File("."))))
                .analysis(
                        Map.of(languageCode,
                                new LuceneAnalysisConfig.Analysis.Builder().tokenFilters(List.of(
                                        new LuceneAnalysisConfig
                                                .Analysis
                                                .TokenFilters
                                                .Builder()
                                                .name("englishMinimalStem"))))
                ).build();
        LuceneLinguistics linguistics = new LuceneLinguistics(enConfig, new ComponentRegistry<>());
        Iterable<Token> tokens = linguistics.getTokenizer().tokenize("Dogs and Cats", parameters);
        assertEquals(List.of("Dog", "and", "Cat"), tokenStrings(tokens));
    }

    @Test
    public void testStemmerWithStopWords() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, false, true);
        String languageCode = Language.ENGLISH.languageCode();
        LuceneAnalysisConfig enConfig = new LuceneAnalysisConfig.Builder()
                .configDir(Optional.of(FileReference.mockFileReferenceForUnitTesting(new File("."))))
                .analysis(
                        Map.of(languageCode,
                                new LuceneAnalysisConfig.Analysis.Builder().tokenFilters(List.of(
                                        new LuceneAnalysisConfig
                                                .Analysis
                                                .TokenFilters
                                                .Builder()
                                                .name("englishMinimalStem"),
                                        new LuceneAnalysisConfig
                                                .Analysis
                                                .TokenFilters
                                                .Builder()
                                                .name("stop")
                                                .conf("words", "stopwords.txt"))))
                ).build();
        LuceneLinguistics linguistics = new LuceneLinguistics(enConfig, new ComponentRegistry<>());
        Iterable<Token> tokens = linguistics.getTokenizer().tokenize("Dogs and Cats", parameters);
        assertEquals(List.of("Dog", "Cat"), tokenStrings(tokens));
    }

    @Test
    public void testOptionalPath() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, false, true);
        String languageCode = Language.ENGLISH.languageCode();
        LuceneAnalysisConfig enConfig = new LuceneAnalysisConfig.Builder()
                .analysis(
                        Map.of(languageCode,
                                new LuceneAnalysisConfig.Analysis.Builder().tokenFilters(List.of(
                                        new LuceneAnalysisConfig
                                                .Analysis
                                                .TokenFilters
                                                .Builder()
                                                .name("englishMinimalStem"))))
                ).build();
        LuceneLinguistics linguistics = new LuceneLinguistics(enConfig, new ComponentRegistry<>());
        Iterable<Token> tokens = linguistics.getTokenizer().tokenize("Dogs and Cats", parameters);
        assertEquals(List.of("Dog", "and", "Cat"), tokenStrings(tokens));
    }

    @Test
    public void testOptionalPathWithClasspathResources() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, false, true);
        String languageCode = Language.ENGLISH.languageCode();
        LuceneAnalysisConfig enConfig = new LuceneAnalysisConfig.Builder()
                .analysis(
                        Map.of(languageCode,
                                new LuceneAnalysisConfig.Analysis.Builder().tokenFilters(List.of(
                                        new LuceneAnalysisConfig
                                                .Analysis
                                                .TokenFilters
                                                .Builder()
                                                .name("englishMinimalStem"),
                                        new LuceneAnalysisConfig
                                                .Analysis
                                                .TokenFilters
                                                .Builder()
                                                .name("stop")
                                                .conf("words", "classpath-stopwords.txt"))))
                ).build();
        LuceneLinguistics linguistics = new LuceneLinguistics(enConfig, new ComponentRegistry<>());
        Iterable<Token> tokens = linguistics.getTokenizer().tokenize("Dogs and Cats", parameters);
        assertEquals(List.of("and", "Cat"), tokenStrings(tokens));
    }

    @Test
    public void compositeConfigKey() {
        String reversingAnalyzerKey = Language.ENGLISH.languageCode()
                + "/"
                + StemMode.ALL;
        LuceneAnalysisConfig enConfig = new LuceneAnalysisConfig.Builder()
                .analysis(
                        Map.of(reversingAnalyzerKey,
                                new LuceneAnalysisConfig.Analysis.Builder().tokenFilters(List.of(
                                        new LuceneAnalysisConfig
                                                .Analysis
                                                .TokenFilters
                                                .Builder()
                                                .name("reverseString"))))
                ).build();
        LuceneLinguistics linguistics = new LuceneLinguistics(enConfig, new ComponentRegistry<>());
        // Matching StemMode
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, false, true);
        Iterable<Token> tokens = linguistics.getTokenizer().tokenize("Dogs and Cats", parameters);
        assertEquals(List.of("sgoD", "dna", "staC"), tokenStrings(tokens));
        // StemMode is different
        parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.BEST, false, true);
        Iterable<Token> stemModeTokens = linguistics.getTokenizer().tokenize("Dogs and Cats", parameters);
        assertEquals(List.of("dog", "cat"), tokenStrings(stemModeTokens));

    }
}

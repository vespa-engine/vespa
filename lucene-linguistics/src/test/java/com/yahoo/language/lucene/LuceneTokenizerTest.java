// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.lucene;

import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.FileReference;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.StemList;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
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

    @Test
    public void testStemmingMultiple() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, false, true);
        String languageCode = Language.ENGLISH.languageCode();
        var analyzer = new MockAnalyzer();
        var registry = new ComponentRegistry<Analyzer>();
        registry.register(new ComponentId(languageCode), analyzer);
        LuceneLinguistics linguistics = new LuceneLinguistics( new LuceneAnalysisConfig.Builder().build(), registry);
        List<Token> tokens = iterableToList(linguistics.getTokenizer().tokenize("Dogs and cats", parameters));
        assertEquals(3, tokens.size());
        assertEquals("Dogs", tokens.get(0).getStem(0));
        assertEquals("DOGS", tokens.get(0).getStem(1));
        assertEquals("and",  tokens.get(1).getStem(0));
        assertEquals("AND",  tokens.get(1).getStem(1));
        assertEquals("cats", tokens.get(2).getStem(0));
        assertEquals("CATS", tokens.get(2).getStem(1));
    }

    private static class MockAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            // Tokenizer splits text on whitespace
            Tokenizer source = new WhitespaceTokenizer();
            TokenStream filter = new DuplicateTokenFilter(source);
            return new TokenStreamComponents(source, filter);
        }
    }

    /** A token filter which emits both the lower-and upper case variant of each token, on the same position. */
    private static class DuplicateTokenFilter extends TokenFilter {

        private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
        private final PositionIncrementAttribute position = addAttribute(PositionIncrementAttribute.class);

        private State savedState = null;
        private boolean emitUppercase = false;

        protected DuplicateTokenFilter(TokenStream input) {
            super(input);
        }

        @Override
        public boolean incrementToken() throws IOException {
            if (emitUppercase) {
                restoreState(savedState);
                String value = term.toString().toUpperCase();
                term.setEmpty();
                term.append(value);
                position.setPositionIncrement(0); // same position
                emitUppercase = false;
                return true;
            }

            if (input.incrementToken()) {
                savedState = captureState();
                emitUppercase = true;
                return true;
            }

            return false;
        }
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

    private List<String> tokenToStrings(Iterable<Token> tokens) {
        List<String> tokenList = new ArrayList<>();
        tokens.forEach(token -> tokenList.add(token.toString()));
        return tokenList;
    }

    private List<String> tokenStrings(Iterable<Token> tokens) {
        List<String> tokenList = new ArrayList<>();
        tokens.forEach(token -> tokenList.add(token.getTokenString()));
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

    @Test
    public void testSynonymConfiguration() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, false, true);
        String languageCode = Language.ENGLISH.languageCode();
        var analysis = new LuceneAnalysisConfig.Analysis.Builder();
        analysis.tokenizer.name("whitespace");

        analysis.tokenFilters.add(tokenFilter("asciiFolding"));

        var synonymGraphFilter = tokenFilter("synonymGraph");
        synonymGraphFilter.conf.put("synonyms", "synonyms.txt");
        synonymGraphFilter.conf.put("ignoreCase", "true");
        synonymGraphFilter.conf.put("expand", "true");
        analysis.tokenFilters.add(synonymGraphFilter);

        var stopFilter = tokenFilter("stop");
        stopFilter.conf.put("words", "stopwords.txt");
        stopFilter.conf.put("ignoreCase", "true");
        analysis.tokenFilters.add(stopFilter);

        var wordDelimiterFilter = tokenFilter("wordDelimiterGraph");
        wordDelimiterFilter.conf.put("generateWordParts", "1");
        wordDelimiterFilter.conf.put("generateNumberParts", "1");
        wordDelimiterFilter.conf.put("catenateWords", "1");
        wordDelimiterFilter.conf.put("catenateNumbers", "1");
        wordDelimiterFilter.conf.put("catenateAll", "1");
        wordDelimiterFilter.conf.put("splitOnCaseChange", "0");
        wordDelimiterFilter.conf.put("splitOnNumerics", "1");
        wordDelimiterFilter.conf.put("stemEnglishPossessive", "1");
        wordDelimiterFilter.conf.put("preserveOriginal", "1");
        wordDelimiterFilter.conf.put("protected", "wordDelimiterGraphFilterFactoryProtected.txt");
        analysis.tokenFilters.add(wordDelimiterFilter);

        analysis.tokenFilters.add(tokenFilter("lowercase"));

        analysis.tokenFilters.add(tokenFilter("removeDuplicates"));

        var config = new LuceneAnalysisConfig.Builder()
                                             .configDir(Optional.of(FileReference.mockFileReferenceForUnitTesting(new File("."))))
                                             .analysis(Map.of(languageCode, analysis));
        LuceneLinguistics linguistics = new LuceneLinguistics(config.build(), new ComponentRegistry<>());

        Iterable<Token> tokens = linguistics.getTokenizer().tokenize("Wi-Fi Sticker", parameters);
        assertEquals(List.of("token 'wi-fi' (stems: [wifi, wi], original: 'Wi-Fi')",
                             "token 'fi' (original: 'Fi')",
                             "token 'sticker' (original: 'Sticker')"),
                     tokenToStrings(tokens));

        assertEquals("token 'c++' (original: 'C++')",
                     linguistics.getTokenizer().tokenize("C++", parameters).iterator().next().toString());
        assertEquals("token 'ship' (stems: [boat], original: 'boat')",
                     linguistics.getTokenizer().tokenize("boat", parameters).iterator().next().toString());
    }

    private LuceneAnalysisConfig.Analysis.TokenFilters.Builder tokenFilter(String name) {
        return new LuceneAnalysisConfig.Analysis.TokenFilters.Builder().name(name);
    }

}

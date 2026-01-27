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
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.cjk.CJKBigramFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
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
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, true, true);
        String text = "This is my Text";
        Iterable<Token> tokens = luceneLinguistics().getTokenizer()
                .tokenize(text, parameters);
        assertEquals(List.of("my", "text"), tokenStrings(tokens));
    }

    @Test
    public void testLithuanianTokenizer() {
        var parameters = new LinguisticsParameters(null, Language.LITHUANIAN, StemMode.ALL, true, true);
        String text = "Žalgirio mūšio data yra 1410 metai";
        Iterable<Token> tokens = luceneLinguistics().getTokenizer().tokenize(text, parameters);
        assertEquals(List.of("žalgir", "mūš", "dat", "1410", "met"), tokenStrings(tokens));
    }

    @Test
    public void testStemming() {
        String text = "mūšio";
        var parameters = new LinguisticsParameters(null, Language.LITHUANIAN, StemMode.ALL, true, true);
        List<StemList> tokens = luceneLinguistics().getStemmer().stem(text, parameters);
        assertEquals(1, tokens.size());
        assertEquals("mūš", tokens.get(0).get(0));
    }

    @Test
    public void testStemmingMultiple() {
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
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

    @Test
    public void testMultiWordSynonymExpansion() {
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
        var registry = new ComponentRegistry<Analyzer>();
        registry.register(new ComponentId(Language.ENGLISH.languageCode()), new MultiWordSynonymAnalyzer());
        LuceneLinguistics linguistics = new LuceneLinguistics(new LuceneAnalysisConfig.Builder().build(), registry);

        // "nyc" expands to "new york city" where:
        // - "new" is at same position as "nyc" (positionIncrement=0)
        // - "york" and "city" are at subsequent positions (positionIncrement=1)
        List<Token> tokens = iterableToList(linguistics.getTokenizer().tokenize("visit nyc today", parameters));

        // Expected: 5 tokens - "visit", "nyc" (with "new" as stem), "york", "city", "today"
        // Potential bug: offset-based check groups all same-offset tokens as stems,
        // producing only 3 tokens with "york" and "city" incorrectly added as stems of "nyc"
        assertEquals(5, tokens.size());
        assertEquals("visit", tokens.get(0).getTokenString());
        assertEquals("nyc", tokens.get(1).getTokenString());
        assertEquals("new", tokens.get(1).getStem(1));  // "new" is a stem of "nyc"
        assertEquals(2, tokens.get(1).getNumStems());   // only "nyc" and "new", not "york"/"city"
        assertEquals("york", tokens.get(2).getTokenString());
        assertEquals("city", tokens.get(3).getTokenString());
        assertEquals("today", tokens.get(4).getTokenString());
    }

    @Test
    public void testStopwordDoesNotMergeTokens() {
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
        var registry = new ComponentRegistry<Analyzer>();
        registry.register(new ComponentId(Language.ENGLISH.languageCode()), new StopwordAnalyzer());
        LuceneLinguistics linguistics = new LuceneLinguistics(new LuceneAnalysisConfig.Builder().build(), registry);

        // Input: "hello the world" with "the" as stopword
        // After stopword removal: "hello" (posInc=1), "world" (posInc=2 due to gap)
        // Bug scenario: if using offset-based check, "world" might incorrectly merge with "hello"
        List<Token> tokens = iterableToList(linguistics.getTokenizer().tokenize("hello the world", parameters));

        assertEquals(2, tokens.size());
        assertEquals("hello", tokens.get(0).getTokenString());
        assertEquals(1, tokens.get(0).getNumStems());  // no stems merged
        assertEquals("world", tokens.get(1).getTokenString());
        assertEquals(1, tokens.get(1).getNumStems());  // "world" is separate, not a stem of "hello"
    }

    @Test
    public void testSingleWordSynonymWithSelf() {
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
        var registry = new ComponentRegistry<Analyzer>();
        registry.register(new ComponentId(Language.ENGLISH.languageCode()), new SingleWordSynonymAnalyzer());
        LuceneLinguistics linguistics = new LuceneLinguistics(new LuceneAnalysisConfig.Builder().build(), registry);

        // Synonym: car => car, automobile
        // "car" expands to "car" and "automobile" at the same position (posInc=0)
        List<Token> tokens = iterableToList(linguistics.getTokenizer().tokenize("buy car today", parameters));

        assertEquals(3, tokens.size());
        assertEquals("buy", tokens.get(0).getTokenString());
        assertEquals(1, tokens.get(0).getNumStems());
        assertEquals("car", tokens.get(1).getTokenString());
        assertEquals(2, tokens.get(1).getNumStems());  // "car" and "automobile"
        assertEquals("automobile", tokens.get(1).getStem(1));
        assertEquals("today", tokens.get(2).getTokenString());
        assertEquals(1, tokens.get(2).getNumStems());
    }

    @Test
    public void testAsciiFoldingPreserveOriginal() {
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
        var registry = new ComponentRegistry<Analyzer>();
        registry.register(new ComponentId(Language.ENGLISH.languageCode()), new AsciiFoldingPreserveAnalyzer());
        LuceneLinguistics linguistics = new LuceneLinguistics(new LuceneAnalysisConfig.Builder().build(), registry);

        // With preserveOriginal=true: "café" produces "cafe" (folded) and "café" (original) at same position
        List<Token> tokens = iterableToList(linguistics.getTokenizer().tokenize("drink café now", parameters));

        assertEquals(3, tokens.size());
        assertEquals("drink", tokens.get(0).getTokenString());
        assertEquals(1, tokens.get(0).getNumStems());
        assertEquals("cafe", tokens.get(1).getTokenString());  // folded version is primary
        assertEquals(2, tokens.get(1).getNumStems());  // "cafe" and "café"
        assertEquals("café", tokens.get(1).getStem(1));  // original preserved as stem
        assertEquals("now", tokens.get(2).getTokenString());
        assertEquals(1, tokens.get(2).getNumStems());
    }

    @Test
    public void testEmptyAndWhitespaceInput() {
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
        Linguistics linguistics = luceneLinguistics();

        // Empty string
        List<Token> emptyTokens = iterableToList(linguistics.getTokenizer().tokenize("", parameters));
        assertEquals(0, emptyTokens.size());

        // Whitespace only
        List<Token> whitespaceTokens = iterableToList(linguistics.getTokenizer().tokenize("   ", parameters));
        assertEquals(0, whitespaceTokens.size());

        // Tabs and newlines
        List<Token> mixedWhitespace = iterableToList(linguistics.getTokenizer().tokenize("\t\n  \r\n", parameters));
        assertEquals(0, mixedWhitespace.size());
    }

    @Test
    public void testCjkBigramSegmentation() {
        var parameters = new LinguisticsParameters(null, Language.JAPANESE, StemMode.ALL, false, true);
        var registry = new ComponentRegistry<Analyzer>();
        registry.register(new ComponentId(Language.JAPANESE.languageCode()), new CjkBigramAnalyzer());
        LuceneLinguistics linguistics = new LuceneLinguistics(new LuceneAnalysisConfig.Builder().build(), registry);

        // "東京都" (Tokyo-to) with CJKBigramFilter produces overlapping bigrams:
        // "東京" (position 1), "京都" (position 2)
        // These should be separate tokens, not stems of each other
        List<Token> tokens = iterableToList(linguistics.getTokenizer().tokenize("東京都", parameters));

        assertEquals(2, tokens.size());
        assertEquals("東京", tokens.get(0).getTokenString());
        assertEquals(1, tokens.get(0).getNumStems());  // no extra stems
        assertEquals("京都", tokens.get(1).getTokenString());
        assertEquals(1, tokens.get(1).getNumStems());  // separate token, not a stem of previous
    }

    @Test
    public void testShingleFilter() {
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
        var registry = new ComponentRegistry<Analyzer>();
        registry.register(new ComponentId(Language.ENGLISH.languageCode()), new ShingleAnalyzer());
        LuceneLinguistics linguistics = new LuceneLinguistics(new LuceneAnalysisConfig.Builder().build(), registry);

        // ShingleFilter with size 2, outputUnigrams=true produces:
        // "a" (pos 1), "a b" (pos 1, same as "a"), "b" (pos 2), "b c" (pos 2), "c" (pos 3)
        // Shingles at same position as their first unigram should be stems, not separate tokens
        List<Token> tokens = iterableToList(linguistics.getTokenizer().tokenize("a b c", parameters));

        // With default ShingleFilter (outputUnigrams=true, size 2):
        // Position 1: "a" with stem "a b"
        // Position 2: "b" with stem "b c"
        // Position 3: "c"
        assertEquals(3, tokens.size());
        assertEquals("a", tokens.get(0).getTokenString());
        assertEquals(2, tokens.get(0).getNumStems());  // "a" and "a b"
        assertEquals("a b", tokens.get(0).getStem(1));
        assertEquals("b", tokens.get(1).getTokenString());
        assertEquals(2, tokens.get(1).getNumStems());  // "b" and "b c"
        assertEquals("b c", tokens.get(1).getStem(1));
        assertEquals("c", tokens.get(2).getTokenString());
        assertEquals(1, tokens.get(2).getNumStems());  // just "c"
    }

    private static class ShingleAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new WhitespaceTokenizer();
            ShingleFilter filter = new ShingleFilter(source, 2, 2);
            filter.setOutputUnigrams(true);
            return new TokenStreamComponents(source, filter);
        }
    }

    private static class CjkBigramAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new StandardTokenizer();
            TokenStream filter = new CJKBigramFilter(source);
            return new TokenStreamComponents(source, filter);
        }
    }

    private static class AsciiFoldingPreserveAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new WhitespaceTokenizer();
            TokenStream filter = new ASCIIFoldingFilter(source, true);  // preserveOriginal=true
            return new TokenStreamComponents(source, filter);
        }
    }

    private static class SingleWordSynonymAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new WhitespaceTokenizer();
            SynonymMap synonymMap;
            try {
                SynonymMap.Builder builder = new SynonymMap.Builder(true);
                // car => car, automobile (unidirectional, keeping original)
                builder.add(new CharsRef("car"), new CharsRef("car"), false);
                builder.add(new CharsRef("car"), new CharsRef("automobile"), false);
                synonymMap = builder.build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            TokenStream filter = new SynonymGraphFilter(source, synonymMap, true);
            return new TokenStreamComponents(source, filter);
        }
    }

    private static class StopwordAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new WhitespaceTokenizer();
            CharArraySet stopwords = new CharArraySet(List.of("the"), true);
            TokenStream filter = new StopFilter(source, stopwords);
            return new TokenStreamComponents(source, filter);
        }
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

    private static class MultiWordSynonymAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new WhitespaceTokenizer();
            TokenStream filter = new MultiWordSynonymFilter(source);
            return new TokenStreamComponents(source, filter);
        }
    }

    /**
     * A token filter simulating multi-word synonym: "nyc => new york city".
     * All expanded tokens share the same offsets but have different position increments.
     */
    private static class MultiWordSynonymFilter extends TokenFilter {

        private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
        private final PositionIncrementAttribute position = addAttribute(PositionIncrementAttribute.class);
        private final OffsetAttribute offset = addAttribute(OffsetAttribute.class);

        private final String[] expansion = {"new", "york", "city"};
        private int expansionIndex = 0;
        private boolean inExpansion = false;
        private int savedStartOffset;
        private int savedEndOffset;

        protected MultiWordSynonymFilter(TokenStream input) {
            super(input);
        }

        @Override
        public boolean incrementToken() throws IOException {
            if (inExpansion && expansionIndex < expansion.length) {
                clearAttributes();
                term.append(expansion[expansionIndex]);
                offset.setOffset(savedStartOffset, savedEndOffset);
                // First expansion token is at same position, rest advance
                position.setPositionIncrement(expansionIndex == 0 ? 0 : 1);
                expansionIndex++;
                if (expansionIndex >= expansion.length) {
                    inExpansion = false;
                }
                return true;
            }

            if (input.incrementToken()) {
                if (term.toString().equalsIgnoreCase("nyc")) {
                    savedStartOffset = offset.startOffset();
                    savedEndOffset = offset.endOffset();
                    inExpansion = true;
                    expansionIndex = 0;
                }
                return true;
            }
            return false;
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
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
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
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
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
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
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
    public void testChineseLanguage() {
        LuceneAnalysisConfig zhConfig = new LuceneAnalysisConfig.Builder()
                                                .configDir(Optional.of(FileReference.mockFileReferenceForUnitTesting(new File("."))))
                                                .analysis(
                                                        Map.of("zh", // Should apply to both zh-hans and zh-hant
                                                               new LuceneAnalysisConfig.Analysis.Builder().tokenFilters(List.of(
                                                                       new LuceneAnalysisConfig
                                                                                   .Analysis
                                                                                   .TokenFilters
                                                                                   .Builder()
                                                                               .name("stop")
                                                                               .conf("words", "stopwords.txt"))))
                                                         ).build();
        LuceneLinguistics linguistics = new LuceneLinguistics(zhConfig, new ComponentRegistry<>());

        // Default processing, for comparison
        var english = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
        assertEquals(List.of("dog", "cat"), tokenStrings(linguistics.getTokenizer().tokenize("Dogs and Cats", english)));

        var simplified = new LinguisticsParameters(null, Language.CHINESE_SIMPLIFIED, StemMode.ALL, false, true);
        assertEquals(List.of("Dogs", "Cats"), tokenStrings(linguistics.getTokenizer().tokenize("Dogs and Cats", simplified)));

        var traditional = new LinguisticsParameters(null, Language.CHINESE_TRADITIONAL, StemMode.ALL, false, true);
        assertEquals(List.of("Dogs", "Cats"), tokenStrings(linguistics.getTokenizer().tokenize("Dogs and Cats", traditional)));
    }

    @Test
    public void testOptionalPath() {
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
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
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
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
        LuceneAnalysisConfig enConfig = new LuceneAnalysisConfig.Builder()
                .analysis(Map.of(Language.ENGLISH.languageCode() + "/" + StemMode.ALL, reverseConfig())).build();
        LuceneLinguistics linguistics = new LuceneLinguistics(enConfig, new ComponentRegistry<>());
        assertReversed(tokenize(null, Language.ENGLISH, StemMode.ALL, linguistics));
        assertStandard(tokenize(null, Language.ENGLISH, StemMode.BEST, linguistics));
    }

    @Test
    public void testSynonymConfiguration() {
        var parameters = new LinguisticsParameters(null, Language.ENGLISH, StemMode.ALL, false, true);
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

    @Test
    public void multiProfileConfig() {
        LuceneAnalysisConfig config = new LuceneAnalysisConfig.Builder()
                                                .analysis(Map.of("en", reverseConfig()))
                                                .analysis(Map.of("en/BEST", englishMinimalStemConfig()))
                                                .analysis(Map.of("profile=p1;language=en", englishMinimalStemConfig()))
                                                .analysis(Map.of("profile=p1; language=fr;stemMode=NONE", englishMinimalStemConfig()))
                                                .analysis(Map.of("profile=p1", reverseConfig()))
                                                .analysis(Map.of("profile=p2", englishMinimalStemConfig()))
                                                .analysis(Map.of("profile=p2;language=en", reverseConfig()))
                                                .build();
        LuceneLinguistics linguistics = new LuceneLinguistics(config, new ComponentRegistry<>());
        assertMinimal( tokenize("p1", Language.ENGLISH, StemMode.ALL, linguistics));
        assertReversed(tokenize("p1", Language.FRENCH,  StemMode.BEST, linguistics));
        assertMinimal( tokenize("p1", Language.FRENCH,  StemMode.NONE, linguistics));
        assertReversed(tokenize("p1", null,    StemMode.BEST, linguistics));
        assertReversed(tokenize("p1", Language.UNKNOWN, StemMode.BEST, linguistics));
        assertReversed(tokenize("p2", Language.ENGLISH, StemMode.ALL, linguistics));
        assertMinimal( tokenize("p2", Language.FRENCH,  StemMode.BEST, linguistics));
        assertReversed(tokenize(null, Language.ENGLISH, StemMode.ALL, linguistics));
        assertMinimal( tokenize(null, Language.ENGLISH, StemMode.BEST, linguistics));
        assertReversed(tokenize("p3", Language.ENGLISH, StemMode.ALL, linguistics));
        assertMinimal( tokenize("p3", Language.ENGLISH, StemMode.BEST, linguistics));
    }

    private Iterable<Token> tokenize(String profile, Language language, StemMode stemMode, Linguistics linguistics) {
        return linguistics.getTokenizer().tokenize("Dogs and Cats", parameters(profile, language, stemMode));
    }

    private LinguisticsParameters parameters(String profile, Language language, StemMode stemMode) {
        return new LinguisticsParameters(profile, language, stemMode, false, true);
    }

    private void assertReversed(Iterable<Token> tokens) {
        assertEquals(List.of("sgoD", "dna", "staC"), tokenStrings(tokens));
    }

    private void assertStandard(Iterable<Token> tokens) {
        assertEquals(List.of("dog", "cat"), tokenStrings(tokens));
    }

    private void assertMinimal(Iterable<Token> tokens) {
        assertEquals(List.of("Dog", "and", "Cat"), tokenStrings(tokens));
    }

    private LuceneAnalysisConfig.Analysis.TokenFilters.Builder tokenFilter(String name) {
        return new LuceneAnalysisConfig.Analysis.TokenFilters.Builder().name(name);
    }

    private LuceneAnalysisConfig.Analysis.Builder reverseConfig() {
        return new LuceneAnalysisConfig.Analysis.Builder().tokenFilters(List.of(
                new LuceneAnalysisConfig
                            .Analysis
                            .TokenFilters
                            .Builder()
                        .name("reverseString")));
    }

    private LuceneAnalysisConfig.Analysis.Builder englishMinimalStemConfig()  {
        return new LuceneAnalysisConfig.Analysis.Builder().tokenFilters(List.of(
                new LuceneAnalysisConfig
                            .Analysis
                            .TokenFilters
                            .Builder()
                        .name("englishMinimalStem")));
    }

}

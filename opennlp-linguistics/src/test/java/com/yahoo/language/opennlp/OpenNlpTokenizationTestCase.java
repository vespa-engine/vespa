// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import ai.vespa.opennlp.OpenNlpConfig;
import com.yahoo.language.Language;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.StemList;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenType;
import com.yahoo.language.process.Tokenizer;
import org.junit.Test;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test of tokenization, with stemming and accent removal
 *
 * @author matskin
 * @author bratseth
 */
public class OpenNlpTokenizationTestCase {

    @Test
    public void testTokenizer() {
        var tester = new OpenNlpLinguisticsTester();
        tester.assertTokenize("This IS a test, 123",
                              List.of("this", "is", "a", "test", "123"),
                              List.of("This", " ", "IS", " ", "a", " ", "test", ",", " ", "123"));
    }

    @Test
    public void testTokenizerCanPreserveCasing() {
        var keepCasing = new LinguisticsParameters(Language.ENGLISH, StemMode.SHORTEST, false, false);
        var tester = new OpenNlpLinguisticsTester();
        tester.assertTokenize("This IS a test",
                              keepCasing,
                              List.of("This", "IS", "a", "test"),
                              List.of("This", " ", "IS", " ", "a", " ", "test"));
    }

    @Test
    public void testUnderscoreTokenization() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.SHORTEST, true, true);
        var tester = new OpenNlpLinguisticsTester();
        tester.assertTokenize("ugcapi_1", parameters, List.of("ugcapi", "1"), null);
    }

    @Test
    public void testEnglishStemming() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, true, true);
        var withKstem = new OpenNlpLinguisticsTester();
        withKstem.assertTokenize("cars walking, jumps pushed", parameters, List.of("car", "walking", "jumps", "pushed"), null);

        var withSnowball = new OpenNlpLinguisticsTester(new OpenNlpConfig.Builder().snowballStemmingForEnglish(true).build());
        withSnowball.assertTokenize("cars walking, jumps pushed", parameters, List.of("car", "walk", "jump", "push"), null);
    }

    @Test
    public void testPhrasesWithPunctuation() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.NONE, false, true);
        var tester = new OpenNlpLinguisticsTester();
        tester.assertTokenize("PHY_101.html a space/time or space-time course", parameters,
                              List.of("phy", "101", "html", "a", "space", "time", "or", "space", "time", "course"),
                              null);
        tester.assertTokenize("PHY_101.", parameters, List.of("phy", "101"), null);
        tester.assertTokenize("101.3", parameters, List.of("101", "3"), null);
    }

    @Test
    public void testDoubleWidthTokenization() {
        var noStems = new LinguisticsParameters(Language.ENGLISH, StemMode.NONE, false, true);
        var shortestStem = new LinguisticsParameters(Language.ENGLISH, StemMode.SHORTEST, false, true);
        var tester = new OpenNlpLinguisticsTester();
        // "sony"
        tester.assertTokenize("\uFF53\uFF4F\uFF4E\uFF59", noStems, List.of("sony"), null);
        tester.assertTokenize("\uFF53\uFF4F\uFF4E\uFF59", shortestStem, List.of("sony"), null);
        // "SONY"
        tester.assertTokenize("\uFF33\uFF2F\uFF2E\uFF39", noStems, List.of("sony"), null);
        tester.assertTokenize("\uFF33\uFF2F\uFF2E\uFF39", shortestStem, List.of("sony"), null);
        // "on"
        tester.assertTokenize("\uFF4F\uFF4E", noStems, List.of("on"), null);
        tester.assertTokenize("\uFF4F\uFF4E", shortestStem, List.of("on"), null);
        // "ON"
        tester.assertTokenize("\uFF2F\uFF2E", noStems, List.of("on"), null);
        tester.assertTokenize("\uFF2F\uFF2E", shortestStem, List.of("on"), null);
        var russianShortestStem = new LinguisticsParameters(Language.RUSSIAN, StemMode.SHORTEST, false, true);
        tester.assertTokenize("наименование", russianShortestStem, List.of("наименован"), null);
    }

    @Test
    public void testLargeTextTokenization() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.NONE, false, true);
        var tester = new OpenNlpLinguisticsTester();
        String input = "teststring ".repeat(100000);
        int numTokens = 0;
        List<Long> pos = new ArrayList<>();
        for (Token t : tester.tokenizer().tokenize(input, parameters)) {
            numTokens++;
            if ((numTokens % 100) == 0) {
                pos.add(t.getOffset());
            }
        }

        assertEquals("Check that all tokens have been tokenized", numTokens, 200000);
        assertTrue("Increasing token pos", tester.assertMonoIncr(pos));
    }

    @Test
    public void testLargeTokenGuard() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.NONE, false, true);
        var tester = new OpenNlpLinguisticsTester();
        String input = "ab".repeat(128 * 256);
        Iterator<Token> it = tester.tokenizer().tokenize(input, parameters).iterator();
        assertTrue(it.hasNext());
        assertNotNull(it.next().getTokenString());
        assertFalse(it.hasNext());
    }

    @Test
    public void testTokenIterator() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.NONE, false, true);
        var tester = new OpenNlpLinguisticsTester();
        Iterator<Token> it = tester.tokenizer().tokenize("", parameters).iterator();
        assertFalse(it.hasNext());
        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {
            // success
        }

        it = tester.tokenizer().tokenize("", parameters).iterator();
        assertFalse(it.hasNext());

        it = tester.tokenizer().tokenize("one two three", parameters).iterator();
        assertNotNull(it.next());
        assertNotNull(it.next());
        assertNotNull(it.next());
        assertNotNull(it.next());
        assertNotNull(it.next());
        assertFalse(it.hasNext());
    }

    @Test
    public void testGetOffsetLength() {
        var parameters = new LinguisticsParameters(Language.GERMAN, StemMode.SHORTEST, false, true);
        var tester = new OpenNlpLinguisticsTester();
        String input = "Deka-Chef Weber r\u00e4umt Kommunikationsfehler ein";
        long[] expOffset = { 0, 4, 5, 9, 10, 15, 16, 21, 22, 42, 43 };
        int[] len = { 4, 1, 4, 1, 5, 1, 5, 1, 20, 1, 3 };

        int idx = 0;
        for (Token token : tester.tokenizer().tokenize(input, parameters)) {
            assertEquals("Token offset for token #" + idx, expOffset[idx], token.getOffset());
            assertEquals("Token len for token #" + idx, len[idx], token.getOrig().length());
            idx++;
        }
    }

    @Test
    public void testRecursiveDecompose() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.SHORTEST, false, true);
        var tester = new OpenNlpLinguisticsTester();
        for (Token t : tester.tokenizer().tokenize("\u00a510%", parameters)) {
            tester.recurseDecompose(t);
        }
    }

    @Test
    public void testIndexability() {
        var tester = new OpenNlpLinguisticsTester();
        String input = "tafsirnya\u0648\u0643\u064F\u0646\u0652";
        for (StemMode stemMode : new StemMode[] { StemMode.NONE, StemMode.SHORTEST }) {
            for (Language l : List.of(Language.INDONESIAN, Language.ENGLISH, Language.ARABIC)) {
                for (boolean accentDrop : new boolean[] { true, false }) {
                    var parameters = new LinguisticsParameters(l, stemMode, accentDrop, true);
                    for (Token token : tester.tokenizer().tokenize(input, parameters)) {
                        if (token.getTokenString().isEmpty()) {
                            assertFalse(token.isIndexable());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testTokenizeEmojis() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, true, true);
        var tester = new OpenNlpLinguisticsTester();
        String emoji1 = "\uD83D\uDD2A"; // 🔪
        Iterator<Token> tokens1 = tester.tokenizer().tokenize(emoji1, parameters).iterator();
        assertTrue(tokens1.hasNext());
        assertEquals(emoji1, tokens1.next().getTokenString());
        assertFalse(tokens1.hasNext());

        String emoji2 = "\uD83D\uDE00"; // 😀
        Iterator<Token> tokens2 = tester.tokenizer().tokenize(emoji1 + emoji2, parameters).iterator();
        assertTrue(tokens2.hasNext());
        assertEquals(emoji1, tokens2.next().getTokenString());
        assertEquals(emoji2, tokens2.next().getTokenString());
        assertFalse(tokens2.hasNext());
    }

    @Test
    public void testStemEmojis() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, true, true);
        var stemmer = new OpenNlpLinguistics().getStemmer();
        String emoji = "\uD83D\uDD2A"; // 🔪
        List<StemList> stems = stemmer.stem(emoji, parameters);
        assertEquals(1, stems.size());
        var stemList = stems.get(0);
        assertEquals(1, stemList.size());
        assertEquals(emoji, stemList.get(0));
    }

    @Test
    public void testStemGreek() {
        var parameters = new LinguisticsParameters(Language.GREEK, StemMode.ALL, true, true);
        var stemmer = new OpenNlpLinguistics().getStemmer();
        String input = "οὖν";
        String output = "ουν";
        List<StemList> stems = stemmer.stem(input, parameters);
        assertEquals(1, stems.size());
        var stemList = stems.get(0);
        assertEquals(1, stemList.size());
        assertEquals(output, stemList.get(0));
    }

    @Test
    public void testTokenTypes() {
        testTokenTypes(Language.ENGLISH);
        testTokenTypes(Language.SPANISH);
    }

    public void testTokenTypes(Language language) {
        var tester = new OpenNlpLinguisticsTester();
        assertEquals(TokenType.ALPHABETIC, tester.tokenize("word", language).iterator().next().getType());
        assertEquals(TokenType.NUMERIC, tester.tokenize("123", language).iterator().next().getType());
        assertEquals(TokenType.SPACE, tester.tokenize(" ", language).iterator().next().getType());
        assertEquals(TokenType.PUNCTUATION, tester.tokenize(".", language).iterator().next().getType());
        assertEquals(TokenType.ALPHABETIC, tester.tokenize("123word", language).iterator().next().getType());

        var tokens = tester.tokenize("123 123word word123", language).iterator();
        assertEquals(TokenType.NUMERIC, tokens.next().getType());
        assertEquals(TokenType.SPACE, tokens.next().getType());
        assertEquals(TokenType.ALPHABETIC, tokens.next().getType());
        assertEquals(TokenType.SPACE, tokens.next().getType());
        assertEquals(TokenType.ALPHABETIC, tokens.next().getType());
    }

}

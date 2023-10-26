// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;
import com.yahoo.language.simple.SimpleTokenizer;
import org.junit.Test;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static com.yahoo.language.LinguisticsCase.toLowerCase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test of tokenization, with stemming and accent removal
 *
 * @author Mathias Mølster Lidal
 */
public class TokenizationTestCase {

    private final Tokenizer tokenizer = new SimpleTokenizer();

    @Test
    public void testTokenizer() {
        assertTokenize("This is a test, 123",
                       Arrays.asList("this", "is", "a", "test", "123"),
                       Arrays.asList("This", " ", "is", " ", "a", " ", "test", ",", " ", "123"));
    }

    @Test
    public void testUnderScoreTokenization() {
        assertTokenize("ugcapi_1", Language.ENGLISH, StemMode.SHORTEST, true, Arrays.asList("ugcapi", "1"), null);
    }

    @Test
    public void testPhrasesWithPunctuation() {
        assertTokenize("PHY_101.html a space/time or space-time course", Language.ENGLISH, StemMode.NONE,
                       false,
                       Arrays.asList("phy", "101", "html", "a", "space", "time", "or", "space", "time", "course"),
                       null);
        assertTokenize("PHY_101.", Language.ENGLISH, StemMode.NONE, false, Arrays.asList("phy", "101"), null);
        assertTokenize("101.3", Language.ENGLISH, StemMode.NONE, false, Arrays.asList("101", "3"), null);
    }

    @Test
    public void testDoubleWidthTokenization() {
        // "sony"
        assertTokenize("\uFF53\uFF4F\uFF4E\uFF59", Language.ENGLISH, StemMode.NONE, false,
                       List.of("sony"), null);
        assertTokenize("\uFF53\uFF4F\uFF4E\uFF59", Language.ENGLISH, StemMode.SHORTEST, false,
                       List.of("sony"), null);
        // "SONY"
        assertTokenize("\uFF33\uFF2F\uFF2E\uFF39", Language.ENGLISH, StemMode.NONE, false,
                       List.of("sony"), null);
        assertTokenize("\uFF33\uFF2F\uFF2E\uFF39", Language.ENGLISH, StemMode.SHORTEST, false,
                       List.of("sony"), null);
        // "on"
        assertTokenize("\uFF4F\uFF4E", Language.ENGLISH, StemMode.NONE, false,
                       List.of("on"), null);
        assertTokenize("\uFF4F\uFF4E", Language.ENGLISH, StemMode.SHORTEST, false,
                       List.of("on"), null);
        // "ON"
        assertTokenize("\uFF2F\uFF2E", Language.ENGLISH, StemMode.NONE, false,
                       List.of("on"), null);
        assertTokenize("\uFF2F\uFF2E", Language.ENGLISH, StemMode.SHORTEST, false,
                       List.of("on"), null);
    }

    @Test
    public void testLargeTextTokenization() {
        StringBuilder sb = new StringBuilder();
        String s = "teststring ";
        for (int i = 0; i < 100000; i++) {
            sb.append(s);
        }

        String input = sb.toString();

        int numTokens = 0;
        List<Long> pos = new ArrayList<>();
        for (Token t : tokenizer.tokenize(input, Language.ENGLISH, StemMode.NONE, false)) {
            numTokens++;
            if ((numTokens % 100) == 0) {
                pos.add(t.getOffset());
            }
        }

        assertEquals("Check that all tokens have been tokenized", numTokens, 200000);
        assertTrue("Increasing token pos", assertMonoIncr(pos));
    }

    @Test
    public void testLargeTokenGuard() {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < 128 * 256; i++) {
            str.append("ab");
        }
        Iterator<Token> it = tokenizer.tokenize(str.toString(), Language.ENGLISH, StemMode.NONE, false).iterator();
        assertTrue(it.hasNext());
        assertNotNull(it.next().getTokenString());
        assertFalse(it.hasNext());
    }

    @Test
    public void testTokenIterator() {
        Iterator<Token> it = tokenizer.tokenize("", Language.ENGLISH, StemMode.NONE, false).iterator();
        assertFalse(it.hasNext());
        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {
            // success
        }

        it = tokenizer.tokenize("", Language.ENGLISH, StemMode.NONE, false).iterator();
        assertFalse(it.hasNext());

        it = tokenizer.tokenize("one two three", Language.ENGLISH, StemMode.NONE, false).iterator();
        assertNotNull(it.next());
        assertNotNull(it.next());
        assertNotNull(it.next());
        assertNotNull(it.next());
        assertNotNull(it.next());
        assertFalse(it.hasNext());
    }

    @Test
    public void testGetOffsetLength() {
        String input = "Deka-Chef Weber r\u00e4umt Kommunikationsfehler ein";
        long[] expOffset = { 0, 4, 5, 9, 10, 15, 16, 21, 22, 42, 43 };
        int[] len = { 4, 1, 4, 1, 5, 1, 5, 1, 20, 1, 3 };

        int idx = 0;
        for (Token token : tokenizer.tokenize(input, Language.GERMAN, StemMode.SHORTEST, false)) {
            assertEquals("Token offset for token #" + idx, expOffset[idx], token.getOffset());
            assertEquals("Token len for token #" + idx, len[idx], token.getOrig().length());
            idx++;
        }
    }

    @Test
    public void testRecursiveDecompose() {
        for (Token t : tokenizer.tokenize("\u00a510%", Language.ENGLISH, StemMode.SHORTEST, false)) {
            recurseDecompose(t);
        }
    }

    @Test
    public void testIndexability() {
        String input = "tafsirnya\u0648\u0643\u064F\u0646\u0652";
        for (StemMode stemMode : new StemMode[] { StemMode.NONE,
                StemMode.SHORTEST }) {
            for (Language l : new Language[] { Language.INDONESIAN,
                    Language.ENGLISH, Language.ARABIC }) {
                for (boolean accentDrop : new boolean[] { true, false }) {
                    for (Token token : tokenizer.tokenize(input,
                            l, stemMode, accentDrop)) {
                        if (token.getTokenString().length() == 0) {
                            assertFalse(token.isIndexable());
                        }
                    }
                }
            }
        }
    }

    private void recurseDecompose(Token t) {
        assertTrue(t.getOffset() >= 0);
        assertTrue(t.getOrig().length() >= 0);

        int numComp = t.getNumComponents();
        for (int i = 0; i < numComp; i++) {
            Token comp = t.getComponent(i);
            recurseDecompose(comp);
        }
    }

    private boolean assertMonoIncr(Iterable<Long> n) {
        long trailing = -1;
        for (long i : n) {
            if (i < trailing) {
                return false;
            }
            trailing = i;
        }
        return true;
    }

    private void assertTokenize(String input, List<String> indexed, List<String> orig) {
        assertTokenize(input, Language.ENGLISH, StemMode.NONE, false, indexed, orig);
    }

    /**
     * <p>Compare the results of running an input string through the tokenizer with an "index" truth, and an optional
     * "orig" truth.</p>
     *
     * @param input      The text to process, passed to tokenizer.
     * @param language   The language tag, passed to tokenizer.
     * @param stemMode   If stemMode != NONE, test will silently succeed if tokenizer does not do stemming.
     * @param accentDrop Passed to the tokenizer.
     * @param indexed    Compared to the "TokenString" result from the tokenizer.
     * @param orig       Compared to the "Orig" result from the tokenizer.
     */
    private void assertTokenize(String input, Language language, StemMode stemMode, boolean accentDrop,
                                List<String> indexed, List<String> orig) {
        int i = 0;
        int j = 0;
        for (Token token : tokenizer.tokenize(input, language, stemMode, accentDrop)) {
            // System.err.println("got token orig '"+token.getOrig()+"'");
            // System.err.println("got token stem '"+token.getTokenString(stemMode)+"'");
            if (token.getNumComponents() > 0) {
                for (int comp = 0; comp < token.getNumComponents(); comp++) {
                    Token t = token.getComponent(comp);
                    if (t.getType().isIndexable()) {
                        assertEquals("comp index: " + i, indexed.get(i++), toLowerCase(t.getTokenString()));
                    }
                }
            } else {
                if (token.getType().isIndexable()) {
                    assertEquals("exp index: " + i, indexed.get(i++), toLowerCase(token.getTokenString()));
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

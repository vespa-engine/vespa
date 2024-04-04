// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.Language;
import com.yahoo.language.process.AbstractTokenizerTestCase;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenScript;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Steinar Knutsen
 * @author bratseth
 */
public class SimpleTokenizerTestCase extends AbstractTokenizerTestCase {

    @Test
    public void testTokenizingNoStemming() {
        TokenizerTester tester = new TokenizerTester().setStemMode(StemMode.NONE);
        tester.assertTokens("a\u030a tralalala n4lle. \uD800\uDFC8 (old Persian sign Auramazda, sorry if " +
                            "anyone 1s offended by ancien7 gods.Running)",
                            "\u00E5", " ", "tralalala"," ","n4lle", ".", " ","\uD800\uDFC8", " ", "(",
                            "old", " ", "persian", " ", "sign", " ", "auramazda", ",", " ", "sorry", " ",
                            "if", " ", "anyone", " ", "1s", " ", "offended", " ", "by", " ", "ancien7",
                            " ", "gods", ".", "running", ")");
    }

    @Test
    public void testTokenizingStemming() {
        TokenizerTester tester = new TokenizerTester().setStemMode(StemMode.ALL);
        tester.assertTokens("a\u030a tralalala n4lle. \uD800\uDFC8 (old Persian sign Auramazda, sorry if " +
                            "anyone 1s offended by ancien7 gods.Running)",
                            "\u00E5", " ", "tralalala"," ","n4lle", ".", " ","\uD800\uDFC8", " ", "(",
                            "old", " ", "persian", " ", "sign", " ", "auramazda", ",", " ", "sorry", " ",
                            "if", " ", "anyone", " ", "1s", " ", "offend", " ", "by", " ", "ancien7",
                            " ", "gods", ".", "running", ")");
    }

    @Test
    public void testTokenizeEmojis() {
        TokenizerTester tester = new TokenizerTester().setStemMode(StemMode.ALL);

        String emoji1 = "\uD83D\uDD2A"; // 🔪
        String emoji2 = "\uD83D\uDE00"; // 😀
        tester.assertTokens(emoji1, emoji1);
        tester.assertTokens(emoji1 + "foo", emoji1, "foo");
        tester.assertTokens(emoji1 + emoji2, emoji1, emoji2);
    }

    @Test public void testTokenizeScripts() {
        TokenizerTester tester = new TokenizerTester().setStemMode(StemMode.NONE);

        tester.assertTokenScripts("anyone is արևելահայերեն by ancient कार्य",
                TokenScript.LATIN,
                TokenScript.COMMON,
                TokenScript.LATIN,
                TokenScript.COMMON,
                TokenScript.ARMENIAN,
                TokenScript.COMMON,
                TokenScript.LATIN,
                TokenScript.COMMON,
                TokenScript.LATIN,
                TokenScript.COMMON,
                TokenScript.DEVANAGARI);
    }
}


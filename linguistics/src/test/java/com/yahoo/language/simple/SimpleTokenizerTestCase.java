// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.process.AbstractTokenizerTestCase;
import com.yahoo.language.process.StemMode;
import org.junit.Test;

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

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.Language;
import com.yahoo.language.detect.Detection;
import com.yahoo.text.Utf8;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleDetectorTestCase {

    @Test
    public void requireThatLanguageCanDetected() {
        assertLanguage(Language.UNKNOWN, "Hello!");

        // "Chinese language"
        assertLanguage(Language.CHINESE_TRADITIONAL, // CHINESE_SIMPLIFIED input
                       "\u6211\u80FD\u541E\u4E0B\u73BB\u7483\u800C\u4E0D\u4F24\u8EAB\u4F53\u3002");

        // a string from http://www.columbia.edu/kermit/utf8.html that says "I can eat glass (and it doesn't hurt me)".
        assertLanguage(Language.CHINESE_TRADITIONAL, // CHINESE_TRADITIONAL input
                       "\u6211\u80FD\u541E\u4E0B\u73BB\u7483\u800C\u4E0D\u50B7\u8EAB\u9AD4\u3002");

        // four katakana characters from this web page: http://www.japanese-online.com/language/lessons/katakana.htm
        assertLanguage(Language.JAPANESE, "\u30ab\u30bf\u30ab\u30ca");

        // four hiragana characters gotton from web page: http://www.japanese-online.com/language/lessons/hiragana.htm
        assertLanguage(Language.JAPANESE, "\u3072\u3089\u304c\u306a");

        // a string from http://www.columbia.edu/kermit/utf8.html that says "I can eat glass (and it doesn't hurt me)".
        // This is a good test because this string contains not only japanese but chinese characters, so we need to look
        // through it to find the japanese ones.
        assertLanguage(Language.JAPANESE,
                       "\u79c1\u306f\u30ac\u30e9\u30b9\u3092\u98df\u3079\u3089\u308c\u307e\u3059" +
                       "\u3002\u305d\u308c\u306f\u79c1\u3092\u50b7\u3064\u3051\u307e\u305b\u3093" +
                       "\u3002");

        // an introduction on an adobe web page.  What it measn I don't know.
        assertLanguage(Language.KOREAN, "\ud55c\uae00\uacfc");

        // for the sound of "A"
        assertLanguage(Language.KOREAN, "\u314f");

        // a string from http://www.columbia.edu/kermit/utf8.html that says "I can eat glass (and it doesn't hurt me)".
        assertLanguage(Language.KOREAN, "\ub098\ub294 \uc720\ub9ac\ub97c \uba39\uc744 \uc218 \uc788\uc5b4\uc694. " +
                                        "\uadf8\ub798\ub3c4 \uc544\ud504\uc9c0 \uc54a\uc544\uc694");
    }

    @Test
    public void testEncodingGuess() {
        // just some arbitrary data above 127 which is not valid as UTF-8
        byte[] b = new byte[] { (byte)196, (byte)197, (byte)198 };
        Detection d = new SimpleDetector().detect(b, 0, b.length, null);
        assertEquals(Charset.forName("ISO-8859-1"), d.getEncoding());

        // a string from http://www.columbia.edu/kermit/utf8.html that says
        // "I can eat glass (and it doesn't hurt me)".
        b = Utf8.toBytes("\ub098\ub294 \uc720\ub9ac\ub97c \uba39\uc744 \uc218 \uc788\uc5b4\uc694. " +
                         "\uadf8\ub798\ub3c4 \uc544\ud504\uc9c0 \uc54a\uc544\uc694");
        d = new SimpleDetector().detect(b, 0, b.length, null);
        assertEquals(Utf8.getCharset(), d.getEncoding());

        // arbitrary ascii
        b = new byte[] { 31, 32, 33 };
        d = new SimpleDetector().detect(b, 0, b.length, null);
        assertEquals(Charset.forName("US-ASCII"), d.getEncoding());

        // character which is not valid in UTF-8
        b = new byte[] { -1 };
        d = new SimpleDetector().detect(b, 0, b.length, null);
        assertEquals(Charset.forName("ISO-8859-1"), d.getEncoding());

        // UTF-8 which requires more bytes than available
        b = new byte[] { Utf8.toBytes("\u00E5")[0] };
        d = new SimpleDetector().detect(b, 0, b.length, null);
        assertEquals(Charset.forName("ISO-8859-1"), d.getEncoding());
    }

    private static void assertLanguage(Language language, String input) {
        assertEquals(language, new SimpleDetector().detect(input, null).getLanguage());
    }

}

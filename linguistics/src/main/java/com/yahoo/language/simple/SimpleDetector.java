// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.Language;
import com.yahoo.language.detect.Detection;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.detect.Hint;
import com.yahoo.text.Utf8;

import java.nio.ByteBuffer;

/**
 * <p>Includes functionality for determining the langCode from a sample or from the encoding. Currently only Chinese,
 * Japanese and Korean are supported.  There are two ways to guess a String's langCode, by encoding and by character
 * set.  If the encoding is available this is a very good indication of the langCode.  If the encoding is not available,
 * then the actual characters in the string can be used to make an educated guess at the String's langCode.  Recall a
 * String in Java is unicode. Therefore, we can simply look at the unicode blocks of the characters in the string.
 * Unfortunately, its not 100% fool-proof. From what I've been able to determine, Korean characters do not overlap with
 * Japanese or Chinese characters, so their presence is a good indication of Korean.  If a string contains phonetic
 * japanese, this is a good indication of Japanese.  However, Japanese and Chinese characters occupy many of the same
 * character blocks, so if there are no definitive signs of Japanese then it is assumed that the String is Chinese.</p>
 *
 * @author Rich Pito
 */
public class SimpleDetector implements Detector {

    @Override
    public Detection detect(byte[] input, int offset, int length, Hint hint) {
        return new Detection(guessLanguage(input, offset, length), guessEncoding(input), false);
    }

    @Override
    public Detection detect(ByteBuffer input, Hint hint) {
        byte[] buf = new byte[input.remaining()];
        input.get(buf, 0, buf.length);
        return detect(buf, 0, buf.length, hint);
    }

    @Override
    public Detection detect(String input, Hint hint) {
        return new Detection(guessLanguage(input), Utf8.getCharset().name(), false);
    }

    public Language guessLanguage(byte[] buf, int offset, int length) {
        return guessLanguage(Utf8.toString(buf, offset, length));
    }

    public Language guessLanguage(String input) {
        if (input == null || input.length() == 0) {
            return Language.UNKNOWN;
        }

        // used to record the current theory of language guess, in case of ambiguous characters, such as Chinese
        Language soFar = Language.UNKNOWN;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);

            // Check some special cases for Korean.  Korean doesn't
            // overlap with Japanese or Chinese, so this is a good test.
            if ((c >= 0x3200 && c < 0x3220) ||  // parenthesized hangul
                (c >= 0x3260 && c < 0x3280) ||  // circled hangul
                (c >= 0xFFA0 && c < 0xFFE0) ||  // halfwidth hangul
                (c == 0x302E || c == 0x302F) || // hangul tone mark

                // standard Hangul character blocks
                block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                block == Character.UnicodeBlock.HANGUL_JAMO ||
                block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) {
                return Language.KOREAN;
            }
            // katakana phonetic extensions.
            if (0x31f0 <= c && c <= 0x31ff) {
                // See http://www.unicode.org/charts/PDF/U31F0.pdf
                // This is a special case because This range of character
                // codes is classified as unasigned in
                // Character.UnicodeBlock.  But clearly it is assigned as
                // per above.
                return Language.JAPANESE;
            }
            if (0x31f0 <= c && c <= 0x31ff || // these are standard character blocks for japanese characters.
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.KANBUN) {
                // See http://www.unicode.org/charts/PDF/U31F0.pdf
                // This is a special case because This range of character
                // codes is classified as unasigned in
                // Character.UnicodeBlock.  But clearly it is assigned as
                // per above.
                return Language.JAPANESE;
            }
            if (block == Character.UnicodeBlock.CJK_COMPATIBILITY ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT ||
                block == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT ||
                block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B) {
                // seeing one of these chars, we assume that the text is Chinese, until more concrete evidence is found
                soFar = Language.CHINESE_TRADITIONAL;
            }
            if (block == Character.UnicodeBlock.BOPOMOFO ||
                block == Character.UnicodeBlock.BOPOMOFO_EXTENDED) {
                return Language.CHINESE_TRADITIONAL;
            }
            if (block == Character.UnicodeBlock.THAI) {
                return Language.THAI;
            }
        }
        // got to the end, so return the current best guess
        return soFar;
    }

    private boolean isTrailingOctet(byte i) {
        return ((i >>> 6) & 3) == 2;
    }

    // If UTF-8, how many trailing octets are expected?
    private int isLeadingFor(byte c) {
        int i = c & 0xff;
        if ((i & (1 << 7)) == 0) {
            return 0;
        } else if ((i >>> 5) == ((1 << 3) - 2)) {
            return 1;
        } else if ((i >>> 4) == ((1 << 4) - 2)) {
            return 2;
        } else if ((i >>> 3) == ((1 << 5) - 2)) {
            return 3;
        } else if ((i >>> 2) == ((1 << 6) - 2)) {
            return 4;
        } else if ((i >>> 1) == ((1 << 7) - 2)) {
            return 5;
        } else {
            return -1;
        }
    }

    private String guessEncoding(byte[] input) {
        boolean isUtf8 = true;
        boolean hasHighs = false;
        scan:
        for (int i = 0; i < input.length; i++) {
            final int l = isLeadingFor(input[i]);
            if (l < 0 || i + l >= input.length) {
                hasHighs = true;
                isUtf8 = false;
                break;
            }
            switch (l) {
            case 0:
                break;
            case 5:
                isUtf8 = isTrailingOctet(input[++i]);
            case 4:
                isUtf8 &= isTrailingOctet(input[++i]);
            case 3:
                isUtf8 &= isTrailingOctet(input[++i]);
            case 2:
                isUtf8 &= isTrailingOctet(input[++i]);
            case 1:
                isUtf8 &= isTrailingOctet(input[++i]);
                hasHighs = true;
                if (!isUtf8) {
                    break scan;
                }
                break;
            }
        }
        if (hasHighs && isUtf8) {
            return Utf8.getCharset().name();
        } else if (!hasHighs) {
            return "US-ASCII";
        } else {
            return "ISO-8859-1";
        }
    }

}

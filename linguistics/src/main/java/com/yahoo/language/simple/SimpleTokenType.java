// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.process.TokenType;

/**
 * @author arnej27959
 */
public class SimpleTokenType {

    public static TokenType valueOf(int codePoint) {
        switch (Character.getType(codePoint)) {
        case Character.NON_SPACING_MARK:
            // "combining grave accent"
            // and "DEVANAGARI VOWEL SIGN SHORT E" etc
            // (letter-like)
        case Character.COMBINING_SPACING_MARK:
            // "DEVANAGARI VOWEL SIGN SHORT O"
            // and similar (letter-like)
        case Character.LETTER_NUMBER:
            // "SMALL ROMAN NUMERAL SIX" etc (letter-like)
        case Character.UPPERCASE_LETTER:
        case Character.LOWERCASE_LETTER:
        case Character.TITLECASE_LETTER:
        case Character.MODIFIER_LETTER:
        case Character.OTHER_LETTER:
            return TokenType.ALPHABETIC;

        case Character.ENCLOSING_MARK:
            // "enclosing circle" etc is symbol-like
        case Character.MATH_SYMBOL:
        case Character.CURRENCY_SYMBOL:
        case Character.MODIFIER_SYMBOL:
        case Character.OTHER_SYMBOL:
            return TokenType.SYMBOL;

        case Character.OTHER_NUMBER:
            // "SUPERSCRIPT TWO",
            // "DINGBAT CIRCLED SANS-SERIF DIGIT THREE"
            // and more numbers that should mostly normalize
            // to digits
        case Character.DECIMAL_DIGIT_NUMBER:
            return TokenType.NUMERIC;

        case Character.SPACE_SEPARATOR:
        case Character.LINE_SEPARATOR:
        case Character.PARAGRAPH_SEPARATOR:
            return TokenType.SPACE;

        case Character.DASH_PUNCTUATION:
        case Character.START_PUNCTUATION:
        case Character.END_PUNCTUATION:
        case Character.CONNECTOR_PUNCTUATION:
        case Character.OTHER_PUNCTUATION:
        case Character.INITIAL_QUOTE_PUNCTUATION:
        case Character.FINAL_QUOTE_PUNCTUATION:
            return TokenType.PUNCTUATION;

        case Character.CONTROL:
        case Character.FORMAT:
        case Character.SURROGATE:
        case Character.PRIVATE_USE:
        case Character.UNASSIGNED:
            return TokenType.UNKNOWN;
        }
        throw new UnsupportedOperationException(String.valueOf(Character.getType(codePoint)));
    }
}

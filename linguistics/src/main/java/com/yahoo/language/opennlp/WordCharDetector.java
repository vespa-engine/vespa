// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

class WordCharDetector {
    public static boolean isWordChar(int codepoint) {
        int unicodeGeneralCategory = Character.getType(codepoint);
        switch (unicodeGeneralCategory) {
        case Character.LOWERCASE_LETTER:
        case Character.OTHER_LETTER:
        case Character.TITLECASE_LETTER:
        case Character.UPPERCASE_LETTER:
        case Character.MODIFIER_LETTER:
            return true;
/*
 * these are the other categories, currently considered non-word-chars:
 *
        case Character.CONNECTOR_PUNCTUATION:
        case Character.CONTROL:
        case Character.CURRENCY_SYMBOL:
        case Character.DASH_PUNCTUATION:
        case Character.ENCLOSING_MARK:
        case Character.END_PUNCTUATION:
        case Character.FINAL_QUOTE_PUNCTUATION:
        case Character.FORMAT:
        case Character.INITIAL_QUOTE_PUNCTUATION:
        case Character.MATH_SYMBOL:
        case Character.MODIFIER_SYMBOL:
        case Character.NON_SPACING_MARK:
        case Character.OTHER_PUNCTUATION:
        case Character.OTHER_SYMBOL:
        case Character.PRIVATE_USE:
        case Character.START_PUNCTUATION:
        case Character.SURROGATE:
        case Character.UNASSIGNED:
        case Character.DECIMAL_DIGIT_NUMBER:
        case Character.LETTER_NUMBER:
        case Character.OTHER_NUMBER:
        case Character.COMBINING_SPACING_MARK:
        case Character.LINE_SEPARATOR:
        case Character.SPACE_SEPARATOR:
        case Character.PARAGRAPH_SEPARATOR:
 *
 */
        default:
            return false;
        }
    }
}

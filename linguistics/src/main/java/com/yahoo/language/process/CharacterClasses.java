// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

/**
 * Determines the class of a given character. Use this rather than java.lang.Character.
 *
 * @author bratseth
 */
public class CharacterClasses {

    /**
     * Returns true for code points which are letters in unicode 3 or 4, plus some additional characters
     * which are useful to view as letters even though not defined as such in unicode.
     */
    public boolean isLetter(int c) {
        if (Character.isLetter(c)) return true;
        if (Character.isDigit(c) &&  ! isLatin(c)) return true; // Not considering these digits, so treat them as letters

        // Some CJK punctuation defined as word characters
        if (c == '\u3008' || c == '\u3009' || c == '\u300a' || c == '\u300b' ||
            c == '\u300c' || c == '\u300d' || c == '\u300e' ||
            c == '\u300f' || c == '\u3010' || c == '\u3011') {
            return true;
        }
        int type = java.lang.Character.getType(c);
        return type == java.lang.Character.NON_SPACING_MARK ||
               type == java.lang.Character.COMBINING_SPACING_MARK ||
               type == java.lang.Character.ENCLOSING_MARK;
    }

    /**
     * Returns true if the character is in the class "other symbol" - emojis etc.
     */
    public boolean isSymbol(int c) {
        return Character.getType(c) == Character.OTHER_SYMBOL;
    }

    /**
     * Returns true for code points which should be considered digits - same as java.lang.Character.isDigit
     */
    public boolean isDigit(int c) {
        return Character.isDigit(c);
    }

    /** Returns true if this is a latin digit (other digits are not consistently parsed into numbers by Java) */
    public boolean isLatinDigit(int c) {
        return Character.isDigit(c) && isLatin(c);
    }

    /** Returns true if this is a latin character */
    public boolean isLatin(int c) {
        return Character.UnicodeBlock.of(c).equals(Character.UnicodeBlock.BASIC_LATIN);
    }

    /**
     * Convenience, returns isLetter(c) || isDigit(c)
     */
    public boolean isLetterOrDigit(int c) {
        return isLetter(c) || isDigit(c);
    }

}

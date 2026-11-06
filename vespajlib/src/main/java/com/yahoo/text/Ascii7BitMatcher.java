// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.util.BitSet;

/**
 * Fast replacement for regex based validators of simple expressions.
 * It can take a list of legal characters for the the first character,
 * and another list for the following. Limited to 7bit ascii.
 *
 * @author baldersheim
 */
public class Ascii7BitMatcher {

    private final BitSet legalFirst;
    private final BitSet legalRest;
    private static BitSet createBitSet(String legal) {
        BitSet legalChars = new BitSet(128);
        for (int i=0; i < legal.length(); i++) {
            char c = legal.charAt(i);
            if (c < 128) {
                legalChars.set(c);
            } else {
                throw new IllegalArgumentException("Char '" + c + "' at position " + i + " is not valid ascii 7 bit char");
            }
        }
        return legalChars;
    }
    public Ascii7BitMatcher(String legal) {
        this(legal, legal);
    }
    public Ascii7BitMatcher(String legalFirstChar, String legalChars) {
        legalFirst = createBitSet(legalFirstChar);
        legalRest = createBitSet(legalChars);
    }
    private static boolean isAscii7Bit(char c) { return c < 128;}
    private boolean isLegalFirst(char c) {
        return isAscii7Bit(c) && legalFirst.get(c);
    }
    private boolean isLegalRest(char c) {
        return isAscii7Bit(c) && legalRest.get(c);
    }
    public boolean matches(String s) {
        if (s == null || s.isEmpty() || ! isLegalFirst(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if ( ! isLegalRest(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static final String LCC = "abcdefghijklmnopqrstuvwxyz"; // lowercase letters
    private static final String UCC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"; // uppercase letters
    private static final String NUM = "0123456789"; // digits
    private static final String CHS = UCC + LCC; // all letters
    private static final String CAU = "_" + CHS; // letters and underscore
    private static final String CAN = CHS + NUM; // letters and digits
    private static final String CNU = "_" + CAN; // letters, digits and underscore

    static public String asciiLetters() { return CHS; }
    static public String charsAndUnderscore() { return CAU; }
    static public String charsAndNumbers() { return CAN; }
    static public String charsNumbersAndUnderscore() { return CNU; }
}

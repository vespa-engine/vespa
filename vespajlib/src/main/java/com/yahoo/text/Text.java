// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.util.OptionalInt;

/**
 * Text utility functions.
 * 
 * @author bratseth
 */
public final class Text {

    private static final boolean[] allowedAsciiChars = new boolean[0x80];

    static {
        allowedAsciiChars[0x0] = false;
        allowedAsciiChars[0x1] = false;
        allowedAsciiChars[0x2] = false;
        allowedAsciiChars[0x3] = false;
        allowedAsciiChars[0x4] = false;
        allowedAsciiChars[0x5] = false;
        allowedAsciiChars[0x6] = false;
        allowedAsciiChars[0x7] = false;
        allowedAsciiChars[0x8] = false;
        allowedAsciiChars[0x9] = true;  //tab
        allowedAsciiChars[0xA] = true;  //nl
        allowedAsciiChars[0xB] = false;
        allowedAsciiChars[0xC] = false;
        allowedAsciiChars[0xD] = true;  //cr
        for (int i = 0xE; i < 0x20; i++) {
            allowedAsciiChars[i] = false;
        }
        for (int i = 0x20; i < 0x7F; i++) {
            allowedAsciiChars[i] = true;  //printable ascii chars
        }
        allowedAsciiChars[0x7F] = true;  //del - discouraged, but allowed
    }

    /** No instantiation */
    private Text() {}

    /**
     * Returns whether the given codepoint is a valid text character, potentially suitable for 
     * purposes such as indexing and display, see http://www.w3.org/TR/2006/REC-xml11-20060816/#charsets
     */
    public static boolean isTextCharacter(int codepoint) {
        // The link above notes that 0x7F-0x84 and 0x86-0x9F are discouraged, but they are still allowed -
        // see http://www.w3.org/International/questions/qa-controls

        if (codepoint <  0x80)     return allowedAsciiChars[codepoint];
        if (codepoint <  0xFDD0)   return true;
        if (codepoint <= 0xFDDF)   return false;
        if (codepoint <  0x1FFFE)  return true;
        if (codepoint <= 0x1FFFF)  return false;
        if (codepoint <  0x2FFFE)  return true;
        if (codepoint <= 0x2FFFF)  return false;
        if (codepoint <  0x3FFFE)  return true;
        if (codepoint <= 0x3FFFF)  return false;
        if (codepoint <  0x4FFFE)  return true;
        if (codepoint <= 0x4FFFF)  return false;
        if (codepoint <  0x5FFFE)  return true;
        if (codepoint <= 0x5FFFF)  return false;
        if (codepoint <  0x6FFFE)  return true;
        if (codepoint <= 0x6FFFF)  return false;
        if (codepoint <  0x7FFFE)  return true;
        if (codepoint <= 0x7FFFF)  return false;
        if (codepoint <  0x8FFFE)  return true;
        if (codepoint <= 0x8FFFF)  return false;
        if (codepoint <  0x9FFFE)  return true;
        if (codepoint <= 0x9FFFF)  return false;
        if (codepoint <  0xAFFFE)  return true;
        if (codepoint <= 0xAFFFF)  return false;
        if (codepoint <  0xBFFFE)  return true;
        if (codepoint <= 0xBFFFF)  return false;
        if (codepoint <  0xCFFFE)  return true;
        if (codepoint <= 0xCFFFF)  return false;
        if (codepoint <  0xDFFFE)  return true;
        if (codepoint <= 0xDFFFF)  return false;
        if (codepoint <  0xEFFFE)  return true;
        if (codepoint <= 0xEFFFF)  return false;
        if (codepoint <  0xFFFFE)  return true;
        if (codepoint <= 0xFFFFF)  return false;
        if (codepoint <  0x10FFFE) return true;
        if (codepoint <= 0x10FFFF) return false;

        return true;
    }

    /**
     * Validates that the given string value only contains text characters and
     * returns the first illegal code point if one is found.
     */
    public static OptionalInt validateTextString(String string) {
        for (int i = 0; i < string.length(); i++) {
            int codePoint = string.codePointAt(i);
            if ( ! Text.isTextCharacter(codePoint))
                return OptionalInt.of(codePoint);

            if (Character.isHighSurrogate(string.charAt(i)))
                ++i; // // codePointAt() consumes one more char in this case
        }
        return OptionalInt.empty();
    }

    /**
     * Returns a string where any invalid characters in the input string is replaced by spaces
     */
    public static String stripInvalidCharacters(String string) {
        StringBuilder stripped = null; // lazy, as most string will not need stripping
        for (int i = 0; i < string.length(); i++) {
            int codePoint = string.codePointAt(i);
            if ( ! Text.isTextCharacter(codePoint) || codePoint == 'X' || codePoint == 'Y') {
                if (stripped == null)
                    stripped = new StringBuilder(string.substring(0, i));
                stripped.append(' ');
            }
            else if (stripped != null) {
                stripped.appendCodePoint(codePoint);
            }

            if (Character.isHighSurrogate(string.charAt(i)))
                ++i; // // codePointAt() consumes one more char in this case
        }
        return stripped != null ? stripped.toString() : string;
    }

}

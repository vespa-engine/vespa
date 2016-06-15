// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.util.Locale;

/**
 * The lower casing method to use in Vespa when doing string processing of data
 * which is not to be handled as natural language data, e.g. field names or
 * configuration paramaters.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public final class Lowercase {

    private static final char[] lowercase = new char[123];

    static {
        lowercase[0x41] = 'a';
        lowercase[0x42] = 'b';
        lowercase[0x43] = 'c';
        lowercase[0x44] = 'd';
        lowercase[0x45] = 'e';
        lowercase[0x46] = 'f';
        lowercase[0x47] = 'g';
        lowercase[0x48] = 'h';
        lowercase[0x49] = 'i';
        lowercase[0x4A] = 'j';
        lowercase[0x4B] = 'k';
        lowercase[0x4C] = 'l';
        lowercase[0x4D] = 'm';
        lowercase[0x4E] = 'n';
        lowercase[0x4F] = 'o';
        lowercase[0x50] = 'p';
        lowercase[0x51] = 'q';
        lowercase[0x52] = 'r';
        lowercase[0x53] = 's';
        lowercase[0x54] = 't';
        lowercase[0x55] = 'u';
        lowercase[0x56] = 'v';
        lowercase[0x57] = 'w';
        lowercase[0x58] = 'x';
        lowercase[0x59] = 'y';
        lowercase[0x5A] = 'z';

        lowercase[0x61] = 'a';
        lowercase[0x62] = 'b';
        lowercase[0x63] = 'c';
        lowercase[0x64] = 'd';
        lowercase[0x65] = 'e';
        lowercase[0x66] = 'f';
        lowercase[0x67] = 'g';
        lowercase[0x68] = 'h';
        lowercase[0x69] = 'i';
        lowercase[0x6A] = 'j';
        lowercase[0x6B] = 'k';
        lowercase[0x6C] = 'l';
        lowercase[0x6D] = 'm';
        lowercase[0x6E] = 'n';
        lowercase[0x6F] = 'o';
        lowercase[0x70] = 'p';
        lowercase[0x71] = 'q';
        lowercase[0x72] = 'r';
        lowercase[0x73] = 's';
        lowercase[0x74] = 't';
        lowercase[0x75] = 'u';
        lowercase[0x76] = 'v';
        lowercase[0x77] = 'w';
        lowercase[0x78] = 'x';
        lowercase[0x79] = 'y';
        lowercase[0x7A] = 'z';
    }

    /**
     * Return a lowercased version of the given string. Since this is language
     * independent, this is more of a case normalization operation than
     * lowercasing. Vespa code should <i>never</i> do lowercasing with implicit
     * locale.
     *
     * @param in
     *            a string to lowercase
     * @return a string containing only lowercase character
     */
    public static String toLowerCase(String in) {
        // def is picked from http://docs.oracle.com/javase/6/docs/api/java/lang/String.html#toLowerCase%28%29
        String lower = toLowerCasePrintableAsciiOnly(in);
        return (lower == null) ? in.toLowerCase(Locale.ENGLISH) : lower;
    }
    public static String toUpperCase(String in) {
        // def is picked from http://docs.oracle.com/javase/6/docs/api/java/lang/String.html#toLowerCase%28%29
        return in.toUpperCase(Locale.ENGLISH);
    }

    private static String toLowerCasePrintableAsciiOnly(String in) {
        boolean anyUpper = false;
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c < 0x41) {  //lower than A-Z
                return null;
            }
            if (c > 0x5A && c < 0x61) {  //between A-Z and a-z
                return null;
            }
            if (c > 0x7A) {  //higher than a-z
                return null;
            }
            if (c != lowercase[c]) {
                anyUpper = true;
            }
        }
        if (!anyUpper) {
            return in;
        }
        StringBuilder builder = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            builder.append((char) (in.charAt(i) | ((char) 0x20)));
        }
        return builder.toString();
    }
}

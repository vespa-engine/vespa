// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.util.Locale;

/**
 * The lower casing method to use in Vespa when doing string processing of data
 * which is not to be handled as natural language data, e.g. field names or
 * configuration paramaters.
 *
 * @author Steinar Knutsen
 */
public final class Lowercase {

    /**
     * Return a lowercased version of the given string. Since this is language
     * independent, this is more of a case normalization operation than
     * lowercasing. Vespa code should <i>never</i> do lowercasing with implicit
     * locale.
     *
     * @param in a string to lowercase
     * @return a string containing only lowercase character
     */
    public static String toLowerCase(String in) {
        return in.toLowerCase(Locale.ENGLISH);

    }
    public static String toUpperCase(String in) {
        return in.toUpperCase(Locale.ENGLISH);
    }

}

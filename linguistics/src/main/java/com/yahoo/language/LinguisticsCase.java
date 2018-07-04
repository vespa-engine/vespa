// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language;

import com.yahoo.text.Lowercase;

import java.util.Locale;

/**
 * This class provides a case normalization operation to be used e.g. when
 * document search should be case insensitive.
 *
 * @author Simon Thoresen Hult
 */
public class LinguisticsCase {

    /**
     * <p>The lower casing method to use in Vespa when doing language independent processing of natural language data.
     * It is placed in a single place to ensure symmetry between e.g. query processing and indexing.</p>
     * <p>Return a lowercased version of the given string. Since this is language independent, this is more of a case
     * normalization operation than lowercasing.</p>
     *
     * @param in The string to lowercase.
     * @return A string containing only lowercase character.
     */
    public static String toLowerCase(String in) {
        // def is picked from http://docs.oracle.com/javase/6/docs/api/java/lang/String.html#toLowerCase%28%29
        // Also, at the time of writing, English is the default language for queries
        return Lowercase.toLowerCase(in);
    }

}

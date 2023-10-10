// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * Utility class parsing a string into a boolean.
 * In contrast to Boolean.parseBoolean in the Java API this parser is strict.
 *
 * @author bratseth
 */
public class BooleanParser {

    /**
     * Returns true if the input string is case insensitive equal to "true" and
     * false if it is case insensitive equal to "false".
     * In any other case an exception is thrown.
     *
     * @param  s the string to parse
     * @return true if s is "true", false if it is "false"
     * @throws IllegalArgumentException if s is not null but neither "true" or "false"
     * @throws NullPointerException if s is null
     */
    public static boolean parseBoolean(String s) {
        if (s==null)
            throw new NullPointerException("Expected 'true' or 'false', got NULL");
        if (s.equalsIgnoreCase("false"))
            return false;
        if (s.equalsIgnoreCase("true"))
            return true;
        throw new IllegalArgumentException("Expected 'true' or 'false', got '" + s + "'");
    }

}

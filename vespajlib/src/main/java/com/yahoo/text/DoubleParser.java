// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * Utility class to parse a String into a double.
 * <p>
 * This was intended as a lower-cost replacement for the standard
 * Double.parseDouble(String) since that method used to cause lock
 * contention.
 * <p>
 * The contention issue is fixed in Java 8u96 so this class is now obsolete.
 *
 * @author arnej27959
 */
@Deprecated
public final class DoubleParser {

    /**
     * Utility method that parses a String and returns a double.
     *
     * @param  data the String to parse
     * @return double parsed value of the string
     * @throws NumberFormatException if the string is not a well-formatted number
     * @throws NullPointerException if the string is a null pointer
     */
    public static double parse(String data) {
        return Double.parseDouble(data);
    }
}

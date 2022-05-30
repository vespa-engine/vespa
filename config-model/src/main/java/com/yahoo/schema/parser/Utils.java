// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

/**
 * @author bjorncs
 */
class Utils {

    private Utils() {}

    // Separate class since javacc does not accept Java code using lambdas
    static int count(String str, char ch) {
        return (int) str.chars().filter(c -> c == ch).count();
    }
}

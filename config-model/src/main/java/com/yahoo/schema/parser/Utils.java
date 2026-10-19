// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

/**
 * @author bjorncs
 */
public class Utils {

    private Utils() {}

    // Separate class since javacc does not accept Java code using lambdas
    public static int count(String str, char ch) {
        return (int) str.chars().filter(c -> c == ch).count();
    }

    /**
     * Returns the given content of an explanation block as clean text:
     * Leading and trailing white space is removed, and the common indentation of the lines
     * following the first one is removed.
     */
    public static String explanationText(String content) {
        int firstNewline = content.indexOf('\n');
        if (firstNewline < 0) return content.strip();
        String firstLine = content.substring(0, firstNewline).strip();
        String rest = content.substring(firstNewline + 1).stripTrailing().stripIndent();
        return (firstLine + "\n" + rest).strip();
    }
}

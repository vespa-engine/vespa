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

    /**
     * Reads the body of a function from the given stream, up to and including the brace closing it,
     * and returns the ranking expression it contains. Nested braces are matched, and braces inside
     * string literals and '#' comments are ignored. The stream must be positioned right after the
     * left brace opening the body. For compatibility, the expression may also be wrapped in
     * "expression: ..." or "expression { ... }"; this wrapping is removed.
     *
     * @throws java.io.IOException if the end of input is reached before the closing brace
     */
    public static String parseFunctionBody(SimpleCharStream input) throws java.io.IOException {
        StringBuilder body = new StringBuilder();
        int depth = 0;
        while (true) {
            char c = input.readChar();
            if (c == '}' && depth == 0) break;
            body.append(c);
            if (c == '{') {
                depth++;
            }
            else if (c == '}') {
                depth--;
            }
            else if (c == '"' || c == '\'') { // string literal: may contain braces
                char quote = c;
                do {
                    c = input.readChar();
                    body.append(c);
                    if (c == '\\') body.append(input.readChar());
                } while (c != quote);
            }
            else if (c == '#') { // comment: may contain braces
                do {
                    c = input.readChar();
                    body.append(c);
                } while (c != '\n');
            }
        }
        return stripExpressionPrefix(body.toString().trim());
    }

    /**
     * Removes the legacy "expression:" or "expression { }" wrapping from a function body, if present,
     * together with any comments preceding it.
     */
    private static String stripExpressionPrefix(String body) {
        String uncommented = body;
        while (uncommented.startsWith("#")) {
            int newline = uncommented.indexOf('\n');
            uncommented = newline < 0 ? "" : uncommented.substring(newline + 1).trim();
        }
        if ( ! uncommented.startsWith("expression")) return body;
        String rest = uncommented.substring("expression".length()).trim();
        if (rest.startsWith(":"))
            return rest.substring(1).trim();
        if (rest.startsWith("{") && rest.endsWith("}"))
            return rest.substring(1, rest.length() - 1).trim();
        return body;
    }

}

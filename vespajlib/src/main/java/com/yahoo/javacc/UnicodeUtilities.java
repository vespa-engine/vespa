// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.javacc;

/**
 * @author Simon Thoresen Hult
 */
public class UnicodeUtilities {

    /**
     * Adds a leading and trailing double quotation mark to the given string. This will escape whatever content is
     * within the string literal.
     *
     * @param string the string to quote
     * @param quote the quote character
     * @return the quoted string
     */
    public static String quote(String string, char quote) {
        StringBuilder b = new StringBuilder();
        b.append(quote);
        for (int i = 0; i < string.length(); ++i) {
            char c = string.charAt(i);
            if (c == quote) {
                b.append("\\").append(c);
            } else {
                b.append(escape(c));
            }
        }
        b.append(quote);
        return b.toString();
    }

    /**
     * Removes leading and trailing quotation mark from the given string. This method will properly unescape whatever
     * content is withing the string literal as well.
     *
     * @param string the string to unquote
     * @return the unquoted string
     */
    public static String unquote(String string) {
        if (string.isEmpty()) return "";

        char startQuote = string.charAt(0);
        if (startQuote != '"' && startQuote != '\'') return string;
        if (string.charAt(string.length() - 1) != startQuote) return string;

        StringBuilder b = new StringBuilder();
        for (int i = 1; i < string.length() - 1; ++i) {
            char c = string.charAt(i);
            if (c == '\\') {
                if (++i == string.length() - 1) break; // done

                c = string.charAt(i);
                if (c == 'f') {
                    b.append("\f");
                } else if (c == 'n') {
                    b.append("\n");
                } else if (c == 'r') {
                    b.append("\r");
                } else if (c == 't') {
                    b.append("\t");
                } else if (c == 'u') {
                    if (++i > string.length() - 4) break; // done
                    try {
                        b.append((char)Integer.parseInt(string.substring(i, i + 4), 16));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(e);
                    }
                    i += 3;
                } else {
                    b.append(c);
                }
            } else if (c == startQuote) {
                throw new IllegalArgumentException();
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static String escape(char c) {
        switch (c) {
            case '\b': return "\\b";
            case '\t': return "\\t";
            case '\n': return "\\n";
            case '\f': return "\\f";
            case '\r': return "\\r";
            case '\\': return "\\\\";
        }
        if (c < 0x20 || c > 0x7e) {
            String unicode = Integer.toString(c, 16);
            return "\\u" + "0000".substring(0, 4 - unicode.length()) + unicode;
        }
        return "" + c;
    }

    public static String generateToken(Predicate predicate) {
        TokenBuilder builder = new TokenBuilder();
        for (int c = 0; c <= 0xffff; ++c) {
            if (!predicate.accepts((char)c)) continue;
            builder.add(c);
        }
        return builder.build();
    }

    public interface Predicate {
        boolean accepts(char c);
    }

    private static class TokenBuilder {

        final StringBuilder token = new StringBuilder();
        int prevC = -1;
        int fromC = 0;
        int charCnt = 0;

        void add(int c) {
            if (prevC + 1 == c) {
                // in range
            } else {
                flushRange();
                fromC = c;
            }
            prevC = c;
        }

        void flushRange() {
            if (fromC > prevC) {
                return; // handle initial condition
            }
            append(fromC);
            if (fromC < prevC) {
                token.append('-');
                append(prevC);
                ++charCnt;
            }
            token.append(',');
            if (++charCnt > 16) {
                token.append('\n');
                charCnt = 0;
            }
        }

        void append(int c) {
            token.append("\"");
            if (c == '\n') {
                token.append("\\n");
            } else if (c == '\r') {
                token.append("\\r");
            } else if (c == '"') {
                token.append("\\\"");
            } else if (c == '\\') {
                token.append("\\\\");
            } else {
                token.append("\\u").append(String.format(java.util.Locale.ROOT, "%04x", c & 0xffff));
            }
            token.append("\"");
        }

        String build() {
            flushRange();
            return token.toString();
        }
    }

}

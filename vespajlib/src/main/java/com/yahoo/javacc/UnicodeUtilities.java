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
     * @param str   The string to quote.
     * @param quote The quote character.
     * @return The quoted string.
     */
    public static String quote(String str, char quote) {
        StringBuilder ret = new StringBuilder();
        ret.append(quote);
        for (int i = 0; i < str.length(); ++i) {
            char c = str.charAt(i);
            if (c == quote) {
                ret.append("\\").append(c);
            } else {
                ret.append(escape(c));
            }
        }
        ret.append(quote);
        return ret.toString();
    }

    /**
     * Removes leading and trailing quotation mark from the given string. This method will properly unescape whatever
     * content is withing the string literal as well.
     *
     * @param str The string to unquote.
     * @return The unquoted string.
     */
    public static String unquote(String str) {
        if (str.length() == 0) {
            return str;
        }
        char quote = str.charAt(0);
        if (quote != '"' && quote != '\'') {
            return str;
        }
        if (str.charAt(str.length() - 1) != quote) {
            return str;
        }
        StringBuilder ret = new StringBuilder();
        for (int i = 1; i < str.length() - 1; ++i) {
            char c = str.charAt(i);
            if (c == '\\') {
                if (++i == str.length() - 1) {
                    break; // done
                }
                c = str.charAt(i);
                if (c == 'f') {
                    ret.append("\f");
                } else if (c == 'n') {
                    ret.append("\n");
                } else if (c == 'r') {
                    ret.append("\r");
                } else if (c == 't') {
                    ret.append("\t");
                } else if (c == 'u') {
                    if (++i > str.length() - 4) {
                        break; // done
                    }
                    try {
                        ret.append((char)Integer.parseInt(str.substring(i, i + 4), 16));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(e);
                    }
                    i += 3;
                } else {
                    ret.append(c);
                }
            } else if (c == quote) {
                throw new IllegalArgumentException();
            } else {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    private static String escape(char c) {
        switch (c) {
        case '\b':
            return "\\b";
        case '\t':
            return "\\t";
        case '\n':
            return "\\n";
        case '\f':
            return "\\f";
        case '\r':
            return "\\r";
        case '\\':
            return "\\\\";
        }
        if (c < 0x20 || c > 0x7e) {
            String unicode = Integer.toString(c, 16);
            return "\\u" + "0000".substring(0, 4 - unicode.length()) + unicode + "";
        }
        return "" + c;
    }

    public static String generateToken(Predicate predicate) {
        TokenBuilder builder = new TokenBuilder();
        for (int c = 0; c <= 0xffff; ++c) {
            if (!predicate.accepts((char)c)) {
                continue;
            }
            builder.add(c);
        }
        return builder.build();
    }

    public static interface Predicate {

        public boolean accepts(char c);
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
                token.append("\\u").append(String.format("%04x", c & 0xffff));
            }
            token.append("\"");
        }

        String build() {
            flushRange();
            return token.toString();
        }
    }
}

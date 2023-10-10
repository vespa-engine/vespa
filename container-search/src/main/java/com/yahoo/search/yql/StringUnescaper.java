// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

class StringUnescaper {

    private static boolean lookaheadOctal(String v, int point) {
        return point < v.length() && "01234567".indexOf(v.charAt(point)) != -1;
    }

    public static String unquote(String token) {
        if (null == token || !(token.startsWith("'") && token.endsWith("'") || token.startsWith("\"") && token.endsWith("\""))) {
            return token;
        }
        // remove quotes from around string and unescape it
        String value = token.substring(1, token.length() - 1);
        // first quickly check to see if \ is present -- if not then there's no escaping and we're done
        int idx = value.indexOf('\\');
        if (idx == -1) {
            return value;
        }
        // the output string will be no bigger than the input string, since escapes add characters
        StringBuilder result = new StringBuilder(value.length());
        int start = 0;
        while (idx != -1) {
            result.append(value.subSequence(start, idx));
            start = idx + 1;
            switch (value.charAt(start)) {
                case 'b':
                    result.append('\b');
                    ++start;
                    break;
                case 't':
                    result.append('\t');
                    ++start;
                    break;
                case 'n':
                    result.append('\n');
                    ++start;
                    break;
                case 'f':
                    result.append('\f');
                    ++start;
                    break;
                case 'r':
                    result.append('\r');
                    ++start;
                    break;
                case '"':
                    result.append('"');
                    ++start;
                    break;
                case '\'':
                    result.append('\'');
                    ++start;
                    break;
                case '\\':
                    result.append('\\');
                    ++start;
                    break;
                case '/':
                    result.append('/');
                    ++start;
                    break;
                case 'u':
                    // hex hex hex hex
                    ++start;
                    result.append((char) Integer.parseInt(value.substring(start, start + 4), 16));
                    start += 4;
                    break;
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                    // octal escape
                    // 1, 2, or 3 bytes
                    // peek ahead
                    if (lookaheadOctal(value, start + 1) && lookaheadOctal(value, start + 2)) {
                        result.append((char) Integer.parseInt(value.substring(start, start + 3), 8));
                        start += 3;
                    } else if (lookaheadOctal(value, start + 1)) {
                        result.append((char) Integer.parseInt(value.substring(start, start + 2), 8));
                        start += 2;
                    } else {
                        result.append((char) Integer.parseInt(value.substring(start, start + 1), 8));
                        start += 1;
                    }
                    break;
                default:
                    // the lexer should be ensuring there are no malformed escapes here, so we'll just blow up
                    throw new IllegalArgumentException("Unknown escape sequence in token: " + token);
            }
            idx = value.indexOf('\\', start);
        }
        result.append(value.subSequence(start, value.length()));
        return result.toString();
    }

    public static String escape(String value) {
        int idx = value.indexOf('\'');
        if (idx == -1) {
            return "\'" + value + "\'";

        }
        StringBuilder result = new StringBuilder(value.length() + 5);
        result.append("'");
        // right now we only escape ' on output
        int start = 0;
        while (idx != -1) {
            result.append(value.subSequence(start, idx));
            start = idx + 1;
            result.append("\\'");
            idx = value.indexOf('\\', start);
        }
        result.append(value.subSequence(start, value.length()));
        result.append("'");
        return result.toString();
    }

}

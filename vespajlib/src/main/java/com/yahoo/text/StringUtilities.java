// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import com.google.common.collect.ImmutableSet;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.util.Set;

/**
 * Escapes strings into and out of a format where they only contain printable characters.
 *
 * Need to duplicate escape / unescape of strings as we have in C++ for java version of system states.
 *
 * @author Haakon Humberset
 */
// TODO: Text utilities should which are still needed should move to Text. This should be deprecated.
public class StringUtilities {

    private static final Charset UTF8 = StandardCharsets.UTF_8;

    private static byte toHex(int val) { return (byte) (val < 10 ? '0' + val : 'a' + (val - 10)); }

    private static class ReplacementCharacters {

        public byte[] needEscape = new byte[256];
        public byte[] replacement1 = new byte[256];
        public byte[] replacement2 = new byte[256];

        public ReplacementCharacters() {
            for (int i=0; i<256; ++i) {
                if (i >= 32 && i <= 126) {
                    needEscape[i] = 0;
                } else {
                    needEscape[i] = 3;
                    replacement1[i] = toHex((i >> 4) & 0xF);
                    replacement2[i] = toHex(i & 0xF);
                }
            }
            makeSimpleEscape('"', '"');
            makeSimpleEscape('\\', '\\');
            makeSimpleEscape('\t', 't');
            makeSimpleEscape('\n', 'n');
            makeSimpleEscape('\r', 'r');
            makeSimpleEscape('\f', 'f');
        }

        private void makeSimpleEscape(char source, char dest) {
            needEscape[source] = 1;
            replacement1[source] = '\\';
            replacement2[source] = (byte) dest;
        }
    }

    private final static ReplacementCharacters replacementCharacters = new ReplacementCharacters();

    public static String escape(String source) { return escape(source, '\0'); }

    /**
     * Escapes strings into a format with only printable ASCII characters.
     *
     * @param source The string to escape
     * @param delimiter Escape this character too, even if it is printable.
     * @return The escaped string
     */
    public static String escape(String source, char delimiter) {
        byte[] bytes = source.getBytes(UTF8);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (byte b : bytes) {
            int val = b;
            if (val < 0) val += 256;
            if (b == delimiter) {
                result.write('\\');
                result.write('x');
                result.write(toHex((val >> 4) & 0xF));
                result.write(toHex(val & 0xF));
            } else if (replacementCharacters.needEscape[val] == 0) {
                result.write(b);
            } else {
                if (replacementCharacters.needEscape[val] == 3) {
                    result.write('\\');
                    result.write('x');
                }
                result.write(replacementCharacters.replacement1[val]);
                result.write(replacementCharacters.replacement2[val]);
            }
        }
        return new String(result.toByteArray(), UTF8);
    }

    public static String unescape(String source) {
        byte[] bytes = source.getBytes(UTF8);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (int i=0; i<bytes.length; ++i) {
            if (bytes[i] != '\\') {
                result.write(bytes[i]);
                continue;
            }
            if (i + 1 == bytes.length) throw new IllegalArgumentException("Found backslash at end of input");

            if (bytes[i + 1] != (byte) 'x') {
                switch (bytes[i + 1]) {
                    case '\\': result.write('\\'); break;
                    case '"': result.write('"'); break;
                    case 't': result.write('\t'); break;
                    case 'n': result.write('\n'); break;
                    case 'r': result.write('\r'); break;
                    case 'f': result.write('\f'); break;
                    default:
                        throw new IllegalArgumentException("Illegal escape sequence \\" + ((char) bytes[i+1]) + " found");
                }
                ++i;
                continue;
            }

            if (i + 3 >= bytes.length) throw new IllegalArgumentException("Found \\x at end of input");

            String hexdigits = "" + ((char) bytes[i + 2]) + ((char) bytes[i + 3]);
            result.write((byte) Integer.parseInt(hexdigits, 16));
            i += 3;
        }
        return new String(result.toByteArray(), UTF8);
    }

    /**
     * Returns the given array flattened to string, with the given separator string
     * @param array the array
     * @param sepString or null
     * @return imploded array
     */
    public static String implode(String[] array, String sepString) {
        if (array==null) return null;
        StringBuilder ret = new StringBuilder();
        if (sepString==null) sepString="";
        for (int i = 0 ; i<array.length ; i++) {
            ret.append(array[i]);
            if (!(i==array.length-1)) ret.append(sepString);
        }
        return ret.toString();
    }

    /**
     * Returns the given list flattened to one with newline between
     *
     * @return flattened string
     */
    public static String implodeMultiline(List<String> lines) {
        if (lines==null) return null;
        return implode(lines.toArray(new String[0]), "\n");
    }

    /**
     * This will truncate sequences in a string of the same character that exceed the maximum
     * allowed length.
     *
     * @return The same string or a new one if truncation is done.
     */
    public static String truncateSequencesIfNecessary(String text, int maxConsecutiveLength) {
        char prev = 0;
        int sequenceCount = 1;
        for (int i = 0, m = text.length(); i < m ; i++) {
            char curr = text.charAt(i);
            if (prev == curr) {
                sequenceCount++;
                if (sequenceCount > maxConsecutiveLength) {
                    return truncateSequences(text, maxConsecutiveLength, i);
                }
            } else {
                sequenceCount = 1;
                prev = curr;
            }
        }
        return text;
    }

    private static String truncateSequences(String text, int maxConsecutiveLength, int firstTruncationPos) {
        char [] truncated = text.toCharArray();
        char prev = truncated[firstTruncationPos];
        int sequenceCount = maxConsecutiveLength + 1;
        int wp=firstTruncationPos;
        for (int rp=wp+1; rp < truncated.length; rp++) {
            char curr = truncated[rp];
            if (prev == curr) {
                sequenceCount++;
                if (sequenceCount <= maxConsecutiveLength) {
                    truncated[wp++] = curr;
                }
            } else {
                truncated[wp++] = curr;
                sequenceCount = 1;
                prev = curr;
            }
        }
        return String.copyValueOf(truncated, 0, wp);
    }

    public static String stripSuffix(String string, String suffix) {
        int index = string.lastIndexOf(suffix);
        return index == -1 ? string : string.substring(0, index);
    }

    /**
     * Adds single quotes around object.toString
     * Example:  '12'
     */
    public static String quote(Object object) {
        return "'" + object.toString() + "'";
    }

    /** Splits a string on both space and comma */
    public static Set<String> split(String s) {
        if (s == null || s.isEmpty()) return Collections.emptySet();
        ImmutableSet.Builder<String> b = new ImmutableSet.Builder<>();
        for (String item : s.split("[\\s,]"))
            if ( ! item.isEmpty())
                b.add(item);
        return b.build();
    }

}

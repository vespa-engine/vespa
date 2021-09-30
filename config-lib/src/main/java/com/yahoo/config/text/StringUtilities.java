// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.text;

import java.nio.charset.Charset;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Escapes strings into and out of a format where they only contain printable characters.
 *
 * Need to duplicate escape / unescape of strings as we have in C++ for java version of system states.
 *
 * @author Haakon Humberset
 */
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
                } else if (i > 127) {
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
     * @param source the string to escape
     * @param delimiter escape this character too, even if it is printable
     * @return the escaped string
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
        return result.toString(UTF8);
    }

}

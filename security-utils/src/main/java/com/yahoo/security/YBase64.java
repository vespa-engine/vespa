// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.security;

import java.util.Base64;

/**
 * Variant of {@link java.util.Base64} with the following modifications:
 * - {@code +} is replaced by {@code .}
 * - {@code /} is replaced by {code _}
 * - {@code =} is replaced by {code -}
 *
 * @author bjorncs
 */
public class YBase64 {
    private YBase64() {}

    public static byte[] decode(byte[] in) {
        byte[] rewritten = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            if (in[i] == '.') rewritten[i] = '+';
            else if (in[i] == '_') rewritten[i] = '/';
            else if (in[i] == '-') rewritten[i] = '=';
            else rewritten[i] = in[i];
        }
        return Base64.getDecoder().decode(rewritten);
    }

    public static byte[] encode(byte[] in) {
        byte[] encoded = Base64.getEncoder().encode(in);
        for (int i = 0; i < encoded.length; i++) {
            if (encoded[i] == '+') encoded[i] = '.';
            else if (encoded[i] == '/') encoded[i] = '_';
            else if (encoded[i] == '=') encoded[i] = '-';
        }
        return encoded;
    }
}

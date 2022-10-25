// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * A small collection of utils for working on arrays of bytes.
 *
 * @author vekterli
 */
public class ArrayUtils {

    /**
     * Returns a new byte array that is the concatenation of all input byte arrays in input order.
     *
     * E.g. <code>concat("A", "BC", "DE", "F") => "ABCDEF"</code>
     */
    public static byte[] concat(byte[]... bufs) {
        int len = 0;
        for (byte[] b : bufs) {
            len += b.length;
        }
        byte[] ret = new byte[len];
        int offset = 0;
        for (byte[] b : bufs) {
            System.arraycopy(b, 0, ret, offset, b.length);
            offset += b.length;
        }
        return ret;
    }

    public static byte[] unhex(String hexStr) {
        return HexFormat.of().parseHex(hexStr);
    }

    public static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    public static byte[] toUtf8Bytes(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    public static String fromUtf8Bytes(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

}

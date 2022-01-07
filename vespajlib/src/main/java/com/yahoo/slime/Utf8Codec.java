// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import com.yahoo.text.Utf8;

/**
 * Helper class for conversion between String and UTF-8 representations.
 */
class Utf8Codec {

    public static String decode(byte[] data, int pos, int len) {
        return Utf8.toString(data, pos, len);
    }
    public static byte[] encode(String str) {
        return Utf8.toBytes(str);
    }

}

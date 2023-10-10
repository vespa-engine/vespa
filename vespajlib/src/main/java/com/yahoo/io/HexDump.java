// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

/**
 * @author bratseth
 */
public class HexDump {

    private static final String HEX_CHARS = "0123456789ABCDEF";

    public static String toHexString(byte[] buf) {
        if (buf == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            int x = b;
            if (x < 0) {
                x += 256;
            }
            sb.append(HEX_CHARS.charAt(x / 16));
            sb.append(HEX_CHARS.charAt(x % 16));
        }
        return sb.toString();
    }

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.text.Utf8;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Serializer;

/**
 * @author Simon Thoresen Hult
 */
public abstract class AbstractRoutableFactory implements RoutableFactory {

    /**
     * Reads a string from the given buffer that was previously written by {@link #encodeString(String,
     * com.yahoo.vespa.objects.Serializer)}.
     *
     * @param in The byte buffer to read from.
     * @return The decoded string.
     */
    public static String decodeString(Deserializer in) {
        int length = in.getInt(null);
        if (length == 0) {
            return "";
        }
        return Utf8.toString(in.getBytes(null, length));
    }

    /**
     * Writes the given string to the given byte buffer in such a way that it can be decoded using {@link
     * #decodeString(com.yahoo.vespa.objects.Deserializer)}.
     *
     * @param str The string to encode.
     * @param out The byte buffer to write to.
     */
    public static void encodeString(String str, Serializer out) {
        if (str == null || str.isEmpty()) {
            out.putInt(null, 0);
        } else {
            byte[] nameBytes = Utf8.toBytes(str);
            out.putInt(null, nameBytes.length);
            out.put(null, nameBytes);
        }
    }
}

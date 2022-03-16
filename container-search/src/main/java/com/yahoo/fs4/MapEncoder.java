// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import com.yahoo.text.Utf8;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A static utility for encoding values to the binary map representation used in fs4 packets.
 *
 * @author bratseth
 */
public class MapEncoder {

    // TODO: Time to refactor

    private static byte [] getUtf8(Object value) {
        if (value == null) {
            return Utf8.toBytes("");
        } else if (value instanceof Tensor) {
            return TypedBinaryFormat.encode((Tensor)value);
        } else {
            return Utf8.toBytes(value.toString());
        }
    }

    /**
     * Encodes a single value as a complete binary map.
     * Does nothing if the value is null.
     *
     * Returns the number of maps encoded - 0 or 1
     */
    public static int encodeSingleValue(String mapName, String key, Object value, ByteBuffer buffer) {
        if (value == null) return 0;

        byte [] utf8 = Utf8.toBytes(mapName);
        buffer.putInt(utf8.length);
        buffer.put(utf8);
        buffer.putInt(1);
        utf8 = Utf8.toBytes(key);
        buffer.putInt(utf8.length);
        buffer.put(utf8);
        utf8 = getUtf8(value);
        buffer.putInt(utf8.length);
        buffer.put(utf8);

        return 1;
    }

    /**
     * Encodes a map as binary.
     * Does nothing if the value is null.
     *
     * Returns the number of maps encoded - 0 or 1
     */
    public static int encodeMap(String mapName, Map<String,?> map, ByteBuffer buffer) {
        if (map.isEmpty()) return 0;

        byte [] utf8 = Utf8.toBytes(mapName);
        buffer.putInt(utf8.length);
        buffer.put(utf8);
        buffer.putInt(map.size());
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            String key = entry.getKey();
            utf8 = Utf8.toBytes(key);
            buffer.putInt(utf8.length);
            buffer.put(utf8);
            Object value = entry.getValue();
            utf8 = getUtf8(value);
            buffer.putInt(utf8.length);
            buffer.put(utf8);
        }

        return 1;
    }

    /**
     * Encodes a multi-map as binary.
     * Does nothing if the value is null.
     *
     * Returns the number of maps encoded - 0 or 1
     */
    public static <T> int encodeMultiMap(String mapName, Map<String,List<T>> map, ByteBuffer buffer) {
        if (map.isEmpty()) return 0;

        byte[] utf8 = Utf8.toBytes(mapName);
        buffer.putInt(utf8.length);
        buffer.put(utf8);
        buffer.putInt(countEntries(map));
        for (Map.Entry<String, List<T>> property : map.entrySet()) {
            String key = property.getKey();
            for (Object value : property.getValue()) {
                utf8 = Utf8.toBytes(key);
                buffer.putInt(utf8.length);
                buffer.put(utf8);
                utf8 = getUtf8(value);
                buffer.putInt(utf8.length);
                buffer.put(utf8);
            }
        }

        return 1;
    }

    private static <T> int countEntries(Map<String, List<T>> value) {
        int entries = 0;
        for (Map.Entry<String, List<T>> property : value.entrySet())
            entries += property.getValue().size();
        return entries;
    }

}

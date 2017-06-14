// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import com.yahoo.text.Utf8;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A static utility for encoding values to the binary map representation used in fs4 packets.
 *
 * @author bratseth
 */
public class MapEncoder {

    // TODO: Time to refactor

    private static final String TYPE_SUFFIX = ".type";
    private static final String TENSOR_TYPE = "tensor";

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
        utf8 = Utf8.toBytes(value.toString());
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
        for (Map.Entry<String, ?> property : map.entrySet()) {
            String key = property.getKey();
            utf8 = Utf8.toBytes(key);
            buffer.putInt(utf8.length);
            buffer.put(utf8);
            utf8 = Utf8.toBytes(property.getValue() != null ? property.getValue().toString() : "");
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
    public static int encodeStringMultiMap(String mapName, Map<String,List<String>> map, ByteBuffer buffer) {
        if (map.isEmpty()) return 0;

        byte [] utf8 = Utf8.toBytes(mapName);
        buffer.putInt(utf8.length);
        buffer.put(utf8);
        buffer.putInt(countStringEntries(map));
        for (Map.Entry<String, List<String>> property : map.entrySet()) {
            String key = property.getKey();
            for (Object value : property.getValue()) {
                utf8 = Utf8.toBytes(key);
                buffer.putInt(utf8.length);
                buffer.put(utf8);
                utf8 = Utf8.toBytes(value.toString());
                buffer.putInt(utf8.length);
                buffer.put(utf8);
            }
        }

        return 1;
    }

    /**
     * Encodes a multi-map as binary.
     * Does nothing if the value is null.
     *
     * Returns the number of maps encoded - 0 or 1
     */
    public static int encodeObjectMultiMap(String mapName, Map<String,List<Object>> map, ByteBuffer buffer) {
        if (map.isEmpty()) return 0;

        byte[] utf8 = Utf8.toBytes(mapName);
        buffer.putInt(utf8.length);
        buffer.put(utf8);
        addTensorTypeInfo(map);
        buffer.putInt(countObjectEntries(map));
        for (Map.Entry<String, List<Object>> property : map.entrySet()) {
            String key = property.getKey();
            for (Object value : property.getValue()) {
                utf8 = Utf8.toBytes(key);
                buffer.putInt(utf8.length);
                buffer.put(utf8);
                if (value instanceof Tensor) {
                    utf8 = TypedBinaryFormat.encode((Tensor)value);
                } else {
                    utf8 = Utf8.toBytes(value.toString());
                }
                buffer.putInt(utf8.length);
                buffer.put(utf8);
            }
        }

        return 1;
    }

    private static void addTensorTypeInfo(Map<String, List<Object>> map) {
        Map<String, Tensor> tensorsToTag = new HashMap<>();
        for (Map.Entry<String, List<Object>> entry : map.entrySet()) {
            for (Object value : entry.getValue()) {
                if (value instanceof Tensor) {
                    tensorsToTag.put(entry.getKey(), (Tensor)value);
                }
            }
        }
        for (Map.Entry<String, Tensor> entry : tensorsToTag.entrySet()) {
            // Ensure that we only have a single tensor associated with each key
            map.put(entry.getKey(), Arrays.asList(entry.getValue()));
            map.put(entry.getKey() + TYPE_SUFFIX, Arrays.asList(TENSOR_TYPE));
        }
    }

    private static int countStringEntries(Map<String, List<String>> value) {
        int entries = 0;
        for (Map.Entry<String, List<String>> property : value.entrySet())
            entries += property.getValue().size();
        return entries;
    }

    private static int countObjectEntries(Map<String, List<Object>> value) {
        int entries = 0;
        for (Map.Entry<String, List<Object>> property : value.entrySet())
            entries += property.getValue().size();
        return entries;
    }

}

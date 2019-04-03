// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol.StringProperty;
import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol.TensorProperty;
import com.google.protobuf.ByteString;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author ollivir
 */
public class MapConverter {
    public static void convertMapTensors(Map<String, Object> map, Consumer<TensorProperty.Builder> inserter) {
        for (var entry : map.entrySet()) {
            var value = entry.getValue();
            if (value instanceof Tensor) {
                byte[] tensor = TypedBinaryFormat.encode((Tensor) value);
                inserter.accept(TensorProperty.newBuilder().setName(entry.getKey()).setValue(ByteString.copyFrom(tensor)));
            }
        }
    }

    public static void convertMapStrings(Map<String, Object> map, Consumer<StringProperty.Builder> inserter) {
        for (var entry : map.entrySet()) {
            var value = entry.getValue();
            if (!(value instanceof Tensor)) {
                inserter.accept(StringProperty.newBuilder().setName(entry.getKey()).addValues(value.toString()));
            }
        }
    }

    public static void convertStringMultiMap(Map<String, List<String>> map, Consumer<StringProperty.Builder> inserter) {
        for (var entry : map.entrySet()) {
            var values = entry.getValue();
            if (values != null) {
                inserter.accept(StringProperty.newBuilder().setName(entry.getKey()).addAllValues(values));
            }
        }
    }

    public static void convertMultiMap(Map<String, List<Object>> map, Consumer<StringProperty.Builder> stringInserter,
            Consumer<TensorProperty.Builder> tensorInserter) {
        for (var entry : map.entrySet()) {
            if (entry.getValue() != null) {
                var key = entry.getKey();
                var stringValues = new LinkedList<String>();
                for (var value : entry.getValue()) {
                    if (value != null) {
                        if (value instanceof Tensor) {
                            byte[] tensor = TypedBinaryFormat.encode((Tensor) value);
                            tensorInserter.accept(TensorProperty.newBuilder().setName(key).setValue(ByteString.copyFrom(tensor)));
                        } else {
                            stringValues.add(value.toString());
                        }
                    }
                }
                if (!stringValues.isEmpty()) {
                    stringInserter.accept(StringProperty.newBuilder().setName(key).addAllValues(stringValues));
                }
            }
        }
    }
}

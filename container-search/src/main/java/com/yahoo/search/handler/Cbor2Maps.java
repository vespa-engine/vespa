// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.yahoo.processing.IllegalInputException;
import com.yahoo.slime.CborFormat;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Decodes a CBOR request body into maps for query processing.
 * <p>
 * Scalar properties and nested objects are dot-flattened into a {@code Map<String, String>},
 * while structured {@code input.*} values (arrays, objects) are kept as Slime {@link Inspector}
 * objects to avoid expensive CBOR → string → double round-trips for tensor data.
 *
 * @author andreer
 */
class Cbor2Maps {

    private final Map<String, String> stringMap = new HashMap<>();
    private final Map<String, Inspector> inspectorMap = new HashMap<>();

    Cbor2Maps(InputStream data) {
        try {
            byte[] raw = data.readAllBytes();
            Inspector root = CborFormat.decode(raw).get();
            if (root.field("error_message").valid()) {
                throw new IllegalInputException("Invalid CBOR request body: " + root.field("error_message").asString());
            }
            if (root.type() != Type.OBJECT) {
                throw new IllegalInputException("Expected CBOR object, got " + root.type());
            }
            flatten(root, "");
        } catch (IOException e) {
            throw new RuntimeException("Problem reading POSTed data", e);
        }
    }

    Map<String, String> stringMap() { return stringMap; }
    Map<String, Inspector> inspectorMap() { return inspectorMap; }

    private void flatten(Inspector object, String prefix) {
        object.traverse((ObjectTraverser) (key, value) -> {
            String fieldName = prefix + key;
            Type type = value.type();
            if (type == Type.STRING || type == Type.LONG || type == Type.DOUBLE || type == Type.BOOL || type == Type.NIX) {
                stringMap.put(fieldName, scalarToString(value));
            } else if (type == Type.ARRAY) {
                if (Json2SingleLevelMap.isInputField(fieldName)) {
                    inspectorMap.put(fieldName, value);
                } else {
                    stringMap.put(fieldName, SlimeUtils.toJson(value));
                }
            } else if (type == Type.OBJECT) {
                if (Json2SingleLevelMap.isInputField(fieldName)) {
                    inspectorMap.put(fieldName, value);
                } else if (Json2SingleLevelMap.isStructuredField(fieldName)) {
                    stringMap.put(fieldName, SlimeUtils.toJson(value));
                } else {
                    flatten(value, fieldName + ".");
                }
            }
        });
    }

    private static String scalarToString(Inspector value) {
        return switch (value.type()) {
            case STRING -> value.asString();
            case LONG -> Long.toString(value.asLong());
            case DOUBLE -> Double.toString(value.asDouble());
            case BOOL -> Boolean.toString(value.asBool());
            case NIX -> "null";
            default -> throw new IllegalStateException("Not a scalar type: " + value.type());
        };
    }

}

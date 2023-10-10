// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author hakonhall
 */
public class SimpleFlagSerializer<T> implements FlagSerializer<T> {
    private final Function<T, JsonNode> serializer;
    private final Predicate<JsonNode> isCorrectType;
    private final Function<JsonNode, T> deserializer;

    public SimpleFlagSerializer(Function<T, JsonNode> serializer,
                                Predicate<JsonNode> isCorrectType,
                                Function<JsonNode, T> deserializer) {
        this.serializer = serializer;
        this.isCorrectType = isCorrectType;
        this.deserializer = deserializer;
    }

    @Override
    public JsonNodeRawFlag serialize(T value) {
        return JsonNodeRawFlag.fromJsonNode(serializer.apply(value));
    }

    @Override
    public T deserialize(RawFlag rawFlag) {
        JsonNode jsonNode = rawFlag.asJsonNode();
        if (!isCorrectType.test(jsonNode)) {
            throw new IllegalArgumentException("Wrong type of JsonNode: " + jsonNode.getNodeType());
        }

        return deserializer.apply(jsonNode);
    }
}

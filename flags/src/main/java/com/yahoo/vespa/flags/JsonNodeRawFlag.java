// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * {@link RawFlag} using Jackson's {@link JsonNode}.
 *
 * @author hakonhall
 */
public class JsonNodeRawFlag implements RawFlag {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final JsonNode jsonNode;

    private JsonNodeRawFlag(JsonNode jsonNode) {
        this.jsonNode = jsonNode;
    }

    public static JsonNodeRawFlag fromJson(String json) {
        return new JsonNodeRawFlag(uncheck(() -> mapper.readTree(json)));
    }

    public static JsonNodeRawFlag fromJsonNode(JsonNode jsonNode) {
        return new JsonNodeRawFlag(jsonNode);
    }

    public static <T> JsonNodeRawFlag fromJacksonClass(T value) {
        return new JsonNodeRawFlag(uncheck(() -> mapper.valueToTree(value)));
    }

    public <T> T toJacksonClass(Class<T> jacksonClass) {
        return uncheck(() -> mapper.treeToValue(jsonNode, jacksonClass));
    }

    public <T> T toJacksonClass(JavaType jacksonClass) {
        return uncheck(() -> mapper.readValue(jsonNode.toString(), jacksonClass));
    }

    @SuppressWarnings("rawtypes")
    public static JavaType constructCollectionType(Class<? extends Collection> collectionClass, Class<?> elementClass) {
        return mapper.getTypeFactory().constructCollectionType(collectionClass, elementClass);
    }

    @Override
    public JsonNode asJsonNode() {
        return jsonNode;
    }

    @Override
    public String asJson() {
        return jsonNode.toString();
    }
}

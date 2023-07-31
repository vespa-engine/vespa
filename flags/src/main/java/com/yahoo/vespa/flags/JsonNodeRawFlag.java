// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * {@link RawFlag} using Jackson's {@link JsonNode}.
 *
 * @author hakonhall
 */
public class JsonNodeRawFlag implements RawFlag {

    private static final AtomicReference<ObjectMapper> mapper = new AtomicReference<>();

    private final JsonNode jsonNode;

    private JsonNodeRawFlag(JsonNode jsonNode) {
        this.jsonNode = jsonNode;
    }

    public static JsonNodeRawFlag fromJson(String json) {
        return new JsonNodeRawFlag(uncheck(() -> objectMapper().readTree(json)));
    }

    public static JsonNodeRawFlag fromJsonNode(JsonNode jsonNode) {
        return new JsonNodeRawFlag(jsonNode);
    }

    public static <T> JsonNodeRawFlag fromJacksonClass(T value) {
        return new JsonNodeRawFlag(uncheck(() -> objectMapper().valueToTree(value)));
    }

    public <T> T toJacksonClass(Class<T> jacksonClass) {
        return uncheck(() -> objectMapper().treeToValue(jsonNode, jacksonClass));
    }

    public <T> T toJacksonClass(JavaType jacksonClass) {
        return uncheck(() -> objectMapper().readValue(jsonNode.toString(), jacksonClass));
    }

    @SuppressWarnings("rawtypes")
    public static JavaType constructCollectionType(Class<? extends Collection> collectionClass, Class<?> elementClass) {
        return objectMapper().getTypeFactory().constructCollectionType(collectionClass, elementClass);
    }

    @Override
    public JsonNode asJsonNode() {
        return jsonNode;
    }

    @Override
    public String asJson() {
        return jsonNode.toString();
    }

    @Override
    public String toString() {
        return "JsonNodeRawFlag{" +
               "jsonNode=" + jsonNode +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonNodeRawFlag that = (JsonNodeRawFlag) o;
        return jsonNode.equals(that.jsonNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonNode);
    }

    /** Initialize object mapper lazily */
    private static ObjectMapper objectMapper() {
        // ObjectMapper is a heavy-weight object so we construct it only when we need it
        return mapper.updateAndGet((objectMapper) -> {
            if (objectMapper != null) return objectMapper;
            return new ObjectMapper();
        });
    }

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JavaType;

import java.util.List;

/**
 * @author freva
 */
public class JacksonArraySerializer<T> implements FlagSerializer<List<T>> {
    private final JavaType type;

    public JacksonArraySerializer(Class<T> clazz) {
        type = JsonNodeRawFlag.constructCollectionType(List.class, clazz);
    }

    @Override
    public List<T> deserialize(RawFlag rawFlag) {
        return JsonNodeRawFlag.fromJsonNode(rawFlag.asJsonNode()).toJacksonClass(type);
    }

    @Override
    public RawFlag serialize(List<T> value) {
        return JsonNodeRawFlag.fromJacksonClass(value);
    }
}

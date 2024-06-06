// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JavaType;

import java.util.List;
import java.util.function.Predicate;

/**
 * @author freva
 */
public class JacksonArraySerializer<T> implements FlagSerializer<List<T>> {
    private final JavaType type;
    private final Predicate<List<T>> validator;

    public JacksonArraySerializer(Class<T> clazz, Predicate<List<T>> validator) {
        type = JsonNodeRawFlag.constructCollectionType(List.class, clazz);
        this.validator = validator;
    }

    @Override
    public List<T> deserialize(RawFlag rawFlag) {
        var list = JsonNodeRawFlag.fromJsonNode(rawFlag.asJsonNode()).<List<T>>toJacksonClass(type);
        if (!validator.test(list)) {
            throw new IllegalArgumentException("Invalid value: " + list);
        }
        return list;
    }

    @Override
    public RawFlag serialize(List<T> value) {
        return JsonNodeRawFlag.fromJacksonClass(value);
    }
}

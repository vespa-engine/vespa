// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.function.Predicate;

/**
 * @author hakonhall
 */
public class JacksonSerializer<T> implements FlagSerializer<T> {
    private final Class<T> jacksonClass;
    private final Predicate<T> validator;

    public JacksonSerializer(Class<T> jacksonClass, Predicate<T> validator) {
        this.jacksonClass = jacksonClass;
        this.validator = validator;
    }

    @Override
    public T deserialize(RawFlag rawFlag) {
        T value = JsonNodeRawFlag.fromJsonNode(rawFlag.asJsonNode()).toJacksonClass(jacksonClass);
        if (!validator.test(value)) {
            throw new IllegalArgumentException("Invalid value: " + rawFlag.asJson());
        }
        return value;
    }

    @Override
    public RawFlag serialize(T value) {
        return JsonNodeRawFlag.fromJacksonClass(value);
    }
}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * @author hakonhall
 */
public class JacksonSerializer<T> implements FlagSerializer<T> {
    private final Class<T> jacksonClass;

    public JacksonSerializer(Class<T> jacksonClass) {
        this.jacksonClass = jacksonClass;
    }

    @Override
    public T deserialize(RawFlag rawFlag) {
        return JsonNodeRawFlag.fromJsonNode(rawFlag.asJsonNode()).toJacksonClass(jacksonClass);
    }

    @Override
    public RawFlag serialize(T value) {
        return JsonNodeRawFlag.fromJacksonClass(value);
    }
}

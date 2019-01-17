// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.List;

/**
 * @author freva
 */
public class JacksonArraySerializer<T> implements FlagSerializer<List<T>> {

    @SuppressWarnings("unchecked")
    @Override
    public List<T> deserialize(RawFlag rawFlag) {
        return (List<T>) JsonNodeRawFlag.fromJsonNode(rawFlag.asJsonNode()).toJacksonClass(List.class);
    }

    @Override
    public RawFlag serialize(List<T> value) {
        return null;
    }
}

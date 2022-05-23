// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * @author hakonhall
 */
public class UnboundJacksonFlag<T> extends UnboundFlagImpl<T, JacksonFlag<T>, UnboundJacksonFlag<T>> {
    public UnboundJacksonFlag(FlagId id, T defaultValue, Class<T> jacksonClass) {
        this(id, defaultValue, new FetchVector(), jacksonClass);
    }

    public UnboundJacksonFlag(FlagId id, T defaultValue, FetchVector defaultFetchVector, Class<T> jacksonClass) {
        super(id, defaultValue, defaultFetchVector,
                new JacksonSerializer<>(jacksonClass),
                (id2, defaultValue2, vector2) -> new UnboundJacksonFlag<>(id2, defaultValue2, vector2, jacksonClass),
                JacksonFlag::new);
    }
}

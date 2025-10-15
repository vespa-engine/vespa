// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.function.Predicate;

/**
 * @author hakonhall
 */
public class UnboundJacksonFlag<T> extends UnboundFlagImpl<T, JacksonFlag<T>, UnboundJacksonFlag<T>> {
    public UnboundJacksonFlag(FlagId id, T defaultValue, FetchVector defaultFetchVector, Class<T> jacksonClass,
                              Predicate<T> validator) {
        super(id, defaultValue, defaultFetchVector,
              new JacksonSerializer<>(jacksonClass, validator),
              (id2, defaultValue2, vector2) -> new UnboundJacksonFlag<>(id2, defaultValue2, vector2, jacksonClass, validator),
              JacksonFlag::new);
    }
}

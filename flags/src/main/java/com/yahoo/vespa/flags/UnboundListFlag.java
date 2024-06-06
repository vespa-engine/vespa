// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.List;
import java.util.function.Predicate;

/**
 * @author freva
 */
public class UnboundListFlag<T> extends UnboundFlagImpl<List<T>, ListFlag<T>, UnboundListFlag<T>> {
    public UnboundListFlag(FlagId id, List<T> defaultValue, Class<T> clazz) {
        this(id, defaultValue, clazz, new FetchVector());
    }

    public UnboundListFlag(FlagId id, List<T> defaultValue, Class<T> clazz, FetchVector defaultFetchVector) {
        this(id, defaultValue, clazz, defaultFetchVector, __ -> true);
    }

    public UnboundListFlag(FlagId id, List<T> defaultValue, Class<T> clazz, FetchVector defaultFetchVector,
                           Predicate<List<T>> validator) {
        super(id, defaultValue, defaultFetchVector,
                new JacksonArraySerializer<T>(clazz, validator),
                (flagId, defVal, fetchVector) -> new UnboundListFlag<>(flagId, defVal, clazz, fetchVector),
                ListFlag::new);
    }
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.List;

/**
 * @author freva
 */
public class UnboundListFlag<T> extends UnboundFlagImpl<List<T>, ListFlag<T>, UnboundListFlag<T>> {
    public UnboundListFlag(FlagId id, List<T> defaultValue, Class<T> clazz) {
        this(id, defaultValue, clazz, new FetchVector());
    }

    public UnboundListFlag(FlagId id, List<T> defaultValue, Class<T> clazz, FetchVector defaultFetchVector) {
        super(id, defaultValue, defaultFetchVector,
                new JacksonArraySerializer<T>(clazz),
                (flagId, defVal, fetchVector) -> new UnboundListFlag<>(flagId, defVal, clazz, fetchVector),
                ListFlag::new);
    }
}

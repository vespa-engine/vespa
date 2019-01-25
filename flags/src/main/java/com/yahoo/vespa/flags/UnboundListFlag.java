// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import javax.annotation.concurrent.Immutable;
import java.util.List;

/**
 * @author freva
 */
@Immutable
public class UnboundListFlag<T> extends UnboundFlagImpl<List<T>, ListFlag<T>, UnboundListFlag<T>> {
    public UnboundListFlag(FlagId id, List<T> defaultValue) {
        this(id, defaultValue, new FetchVector());
    }

    public UnboundListFlag(FlagId id, List<T> defaultValue, FetchVector defaultFetchVector) {
        super(id, defaultValue, defaultFetchVector,
                new JacksonArraySerializer<T>(),
                UnboundListFlag::new,
                ListFlag::new);
    }
}

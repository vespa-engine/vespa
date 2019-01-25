// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import javax.annotation.concurrent.Immutable;
import java.util.List;

/**
 * @author freva
 */
@Immutable
public class ListFlag<T> extends FlagImpl<List<T>, ListFlag<T>> {
    public ListFlag(FlagId id, List<T> defaultValue, FetchVector vector, FlagSerializer<List<T>> serializer, FlagSource source) {
        super(id, defaultValue, vector, serializer, source, ListFlag::new);
    }

    public List<T> value() {
        return boxedValue();
    }
}

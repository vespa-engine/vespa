// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.List;

/**
 * @author freva
 */
public class ListFlag<T> extends FlagImpl<List<T>, ListFlag<T>> {
    public ListFlag(FlagId id, List<T> defaultValue, FetchVector vector, FlagSerializer<List<T>> serializer, FlagSource source) {
        super(id, defaultValue, vector, serializer, source, ListFlag::new);
    }

    @Override
    public ListFlag<T> self() {
        return this;
    }

    public List<T> value() {
        return boxedValue();
    }
}

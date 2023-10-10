// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A {@link FlagSource#fetch(FlagId, FetchVector) fetch} on this flag source will return the {@link RawFlag}
 * from the first (highest priority) flag source that returns a raw flag for the fetch vector.
 *
 * @author hakonhall
 */
public class OrderedFlagSource implements FlagSource {
    private final List<FlagSource> sources;

    /**
     * @param sources Flag sources in descending priority order.
     */
    public OrderedFlagSource(FlagSource... sources) {
        this.sources = Arrays.asList(sources);
    }

    @Override
    public Optional<RawFlag> fetch(FlagId id, FetchVector vector) {
        return sources.stream()
                .map(source -> source.fetch(id, vector))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.vespa.flags.json.FlagData;

import java.util.Map;
import java.util.Optional;

/**
 * An immutable {@link FlagSource} backed by a snapshot of {@link FlagData} entries.
 *
 * @author bjorncs
 */
public class SnapshotFlagSource implements FlagSource {

    private final Map<FlagId, FlagData> data;

    public SnapshotFlagSource(Map<FlagId, FlagData> data) {
        this.data = Map.copyOf(data);
    }

    @Override
    public Optional<RawFlag> fetch(FlagId id, FetchVector vector) {
        return Optional.ofNullable(data.get(id)).flatMap(d -> d.resolve(vector));
    }

    @Override public FlagSource snapshot() { return this; }

}

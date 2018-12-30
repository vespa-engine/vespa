// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags.db;

import com.google.inject.Inject;
import com.yahoo.path.Path;
import com.yahoo.vespa.configserver.flags.FlagsDb;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author hakonhall
 */
public class FlagsDbImpl implements FlagsDb {
    private static final Path ROOT_PATH = Path.fromString("/flags/v1");

    private final Curator curator;

    @Inject
    public FlagsDbImpl(Curator curator) {
        this.curator = curator;
    }

    @Override
    public Optional<FlagData> getValue(FlagId flagId) {
        return curator.getData(getZkPathFor(flagId)).map(FlagData::deserializeUtf8Json);
    }

    @Override
    public void setValue(FlagId flagId, FlagData data) {
        curator.set(getZkPathFor(flagId), data.serializeToUtf8Json());
    }

    @Override
    public Map<FlagId, FlagData> getAllFlags() {
        Map<FlagId, FlagData> flags = new HashMap<>();
        for (String flagId : curator.getChildren(ROOT_PATH)) {
            FlagId id = new FlagId(flagId);
            getValue(id).ifPresent(data -> flags.put(id, data));
        }
        return flags;
    }

    @Override
    public void removeValue(FlagId flagId) {
        curator.delete(getZkPathFor(flagId));
    }

    private static Path getZkPathFor(FlagId flagId) {
        return ROOT_PATH.append(flagId.toString());
    }
}

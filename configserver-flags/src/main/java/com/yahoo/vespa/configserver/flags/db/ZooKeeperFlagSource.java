// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags.db;

import com.yahoo.vespa.configserver.flags.FlagsDb;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.RawFlag;

import java.util.Optional;

/**
 * @author hakonhall
 */
public class ZooKeeperFlagSource implements FlagSource {
    private final FlagsDb flagsDb;

    public ZooKeeperFlagSource(FlagsDb flagsDb) {
        this.flagsDb = flagsDb;
    }

    @Override
    public Optional<RawFlag> fetch(FlagId id, FetchVector vector) {
        return flagsDb.getValue(id).flatMap(data -> data.resolve(vector));
    }
}

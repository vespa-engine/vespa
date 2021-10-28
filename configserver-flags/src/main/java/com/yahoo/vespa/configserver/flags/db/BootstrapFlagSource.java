// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags.db;

import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.RawFlag;
import com.yahoo.vespa.flags.file.FlagDbFile;
import com.yahoo.vespa.flags.json.FlagData;

import java.nio.file.FileSystem;
import java.util.Map;
import java.util.Optional;

/**
 * A flag source that can be used in emergencies, see test for path and format of file.
 *
 * <p>Use file if the ZooKeeper flag makes it difficult to start the config server, would make irreparable damage,
 * or is impossible to change through ZooKeeper due to a bug. This flag source should take precedence over ZooKeeper
 * whenever it has specified the value for a flag.
 *
 * @author hakonhall
 */
public class BootstrapFlagSource implements FlagSource {
    private final Map<FlagId, FlagData> flagData;

    public BootstrapFlagSource(FileSystem fileSystem) {
        // The flags on disk is read once now and never again.
        this.flagData = new FlagDbFile(fileSystem).read();
    }

    @Override
    public Optional<RawFlag> fetch(FlagId id, FetchVector vector) {
        return Optional.ofNullable(flagData.get(id)).flatMap(data -> data.resolve(vector));
    }
}

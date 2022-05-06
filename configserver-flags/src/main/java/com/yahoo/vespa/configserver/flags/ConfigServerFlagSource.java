// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags;

import com.yahoo.component.annotation.Inject;
import com.yahoo.vespa.configserver.flags.db.BootstrapFlagSource;
import com.yahoo.vespa.configserver.flags.db.ZooKeeperFlagSource;
import com.yahoo.vespa.flags.OrderedFlagSource;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

/**
 * @author hakonhall
 */
public class ConfigServerFlagSource extends OrderedFlagSource {
    @Inject
    public ConfigServerFlagSource(FlagsDb flagsDb) {
        this(FileSystems.getDefault(), flagsDb);
    }

    ConfigServerFlagSource(FileSystem fileSystem, FlagsDb flagsDb) {
        super(new BootstrapFlagSource(fileSystem), new ZooKeeperFlagSource(flagsDb));
    }
}

// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags;

import com.google.inject.Inject;
import com.yahoo.vespa.configserver.flags.db.ZooKeeperFlagSource;
import com.yahoo.vespa.flags.FileFlagSource;
import com.yahoo.vespa.flags.OrderedFlagSource;

/**
 * @author hakonhall
 */
public class ConfigServerFlagSource extends OrderedFlagSource {
    @Inject
    public ConfigServerFlagSource(FlagsDb flagsDb) {
        super(new FileFlagSource(), new ZooKeeperFlagSource(flagsDb));
    }
}

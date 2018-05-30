// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionImpl;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionProvider;

import java.io.File;

/**
 * Factory for creating providers that are used to interact with file distribution.
 *
 * @author Ulf Lilleengen
 */
@SuppressWarnings("WeakerAccess")
public class FileDistributionFactory {

    private final ConfigserverConfig configserverConfig;

    @Inject
    public FileDistributionFactory(ConfigserverConfig configserverConfig) {
        this.configserverConfig = configserverConfig;
    }

    public FileDistributionProvider createProvider(File applicationPackage) {
        return new FileDistributionProvider(applicationPackage, new FileDistributionImpl(configserverConfig));
    }

}

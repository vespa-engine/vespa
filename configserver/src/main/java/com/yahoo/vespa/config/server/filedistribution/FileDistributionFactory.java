// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;

/**
 * Factory for creating providers that are used to interact with file distribution.
 *
 * @author Ulf Lilleengen
 */
@SuppressWarnings("WeakerAccess")
public class FileDistributionFactory {

    protected final ConfigserverConfig configserverConfig;

    @Inject
    public FileDistributionFactory(ConfigserverConfig configserverConfig) {
        this.configserverConfig = configserverConfig;
    }

    public FileRegistry createFileRegistry(File applicationPackage) {
        return new FileDBRegistry(new ApplicationFileManager(applicationPackage, new FileDirectory(getFileReferencesDir())));
    }

    public FileDistribution createFileDistribution() {
        return new FileDistributionImpl(getFileReferencesDir());
    }

    public AddFileInterface createFileManager(File applicationDir) {
        return new ApplicationFileManager(applicationDir, new FileDirectory(getFileReferencesDir()));
    }

    protected File getFileReferencesDir() {
        return new File(Defaults.getDefaults().underVespaHome(configserverConfig.fileReferencesDir()));
    }

}

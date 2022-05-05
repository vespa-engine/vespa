// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.component.annotation.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;

/**
 * Factory for creating providers that are used to interact with file distribution.
 *
 * @author Ulf Lilleengen
 */
@SuppressWarnings("WeakerAccess")
public class FileDistributionFactory implements AutoCloseable {

    protected final ConfigserverConfig configserverConfig;
    private final Supervisor supervisor = new Supervisor(new Transport("filedistribution"));

    @Inject
    public FileDistributionFactory(ConfigserverConfig configserverConfig) {
        this.configserverConfig = configserverConfig;
    }

    public FileRegistry createFileRegistry(File applicationPackage) {
        return new FileDBRegistry(createFileManager(applicationPackage));
    }

    public FileDistribution createFileDistribution() {
        return new FileDistributionImpl(supervisor);
    }

    public AddFileInterface createFileManager(File applicationDir) {
        return new ApplicationFileManager(applicationDir, new FileDirectory(getFileReferencesDir()), configserverConfig.hostedVespa());
    }

    protected File getFileReferencesDir() {
        return new File(Defaults.getDefaults().underVespaHome(configserverConfig.fileReferencesDir()));
    }

    public void close() {
        supervisor.transport().shutdown().join();
    }

}

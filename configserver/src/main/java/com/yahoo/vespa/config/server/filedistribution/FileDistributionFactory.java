// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.flags.FlagSource;

import java.io.File;
import java.util.Optional;

/**
 * Factory for creating providers that are used to interact with file distribution.
 *
 * @author Ulf Lilleengen
 */
@SuppressWarnings("WeakerAccess")
public class FileDistributionFactory implements AutoCloseable {

    protected final ConfigserverConfig configserverConfig;
    protected final FileDirectory fileDirectory;
    private final FlagSource flagSource;
    private final Supervisor supervisor = new Supervisor(new Transport("filedistribution"));


    @Inject
    public FileDistributionFactory(ConfigserverConfig configserverConfig, FileDirectory fileDirectory, FlagSource flagSource) {
        this.configserverConfig = configserverConfig;
        this.fileDirectory = fileDirectory;
        this.flagSource = flagSource;
    }

    public FileRegistry createFileRegistry(File applicationPackage, Optional<ApplicationId> owner) {
        return new FileDBRegistry(createFileManager(applicationPackage, owner));
    }

    public FileDistribution createFileDistribution() {
        return new FileDistributionImpl(supervisor);
    }

    public AddFileInterface createFileManager(File applicationDir, Optional<ApplicationId> owner) {
        return new ApplicationFileManager(applicationDir, fileDirectory, configserverConfig.hostedVespa(), owner);
    }

    public FileDirectory fileDirectory() { return fileDirectory; }

    public void close() {
        supervisor.transport().shutdown().join();
    }

}

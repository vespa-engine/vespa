// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory.HashScheme;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.yolean.concurrent.Memoized;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Supplier;

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

    public FileRegistry createFileRegistry(File applicationPackage, ApplicationId owner) {
        return new FileDBRegistry(createFileManager(applicationPackage, owner));
    }

    public FileDistribution createFileDistribution() {
        return new FileDistributionImpl(supervisor);
    }

    public AddFileInterface createFileManager(File applicationDir, ApplicationId owner) {
        int percent = Flags.APPLICATION_UPDATE_ROLLOUT_PERCENT.bindTo(flagSource).with(owner).value();
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("application-update-rollout-percent must be between 0 and 100: " + percent);
        int bucket = Math.floorMod(owner.serializedForm().hashCode(), 100);
        HashScheme scheme = bucket < percent ? HashScheme.SHA256 : HashScheme.LEGACY;
        return new ApplicationFileManager(applicationDir, fileDirectory, configserverConfig.hostedVespa(), Optional.of(owner), scheme);
    }

    // A new session has no saved identity yet. Resolve it only if the loaded registry needs a new reference.
    public AddFileInterface createFileManager(File applicationDir, Supplier<ApplicationId> owner) {
        Supplier<AddFileInterface> manager = new Memoized<>(() -> createFileManager(applicationDir, owner.get()));
        return new AddFileInterface() {
            @Override
            public FileReference addFile(Path path) throws IOException {
                return manager.get().addFile(path);
            }

            @Override
            public FileReference addUri(String uri, Path path) {
                return manager.get().addUri(uri, path);
            }

            @Override
            public FileReference addBlob(ByteBuffer blob, Path path) {
                return manager.get().addBlob(blob, path);
            }
        };
    }

    public FileDirectory fileDirectory() { return fileDirectory; }

    public void close() {
        supervisor.transport().shutdown().join();
    }

}

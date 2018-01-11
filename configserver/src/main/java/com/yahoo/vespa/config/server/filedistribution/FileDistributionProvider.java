// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.jrt.Supervisor;
import com.yahoo.vespa.filedistribution.FileDistributionManager;

import java.io.File;
import java.util.concurrent.locks.Lock;

/**
 * Provides file distribution registry and invoker.
 *
 * @author Ulf Lilleengen
 */
public class FileDistributionProvider {

    private final FileRegistry fileRegistry;
    private final FileDistribution fileDistribution;

    static private class ManagerWrapper implements AddFileInterface {

        private final FileDistributionManager manager;
        ManagerWrapper(FileDistributionManager manager) {
            this.manager = manager;
        }
        @Override
        public FileReference addFile(String relativePath) {
            return new FileReference(manager.addFile(relativePath));
        }

        @Override
        public FileReference addFile(String relativePath, FileReference reference) {
            throw new IllegalStateException("addFile with external reference is not possible with legacy filedistribution.");
        }
    }

    public FileDistributionProvider(Supervisor supervisor, File applicationDir, String zooKeepersSpec,
                                    String applicationId, Lock fileDistributionLock,
                                    boolean disableFileDistributor) {
        ensureDirExists(FileDistribution.getDefaultFileDBPath());
        final FileDistributionManager manager = new FileDistributionManager(
                FileDistribution.getDefaultFileDBPath(), applicationDir,
                zooKeepersSpec, applicationId, fileDistributionLock);
        this.fileDistribution = new CombinedLegacyDistribution(supervisor, new FileDBHandler(manager), disableFileDistributor);
        this.fileRegistry = new CombinedLegacyRegistry(new FileDBRegistry(new ManagerWrapper(manager)),
                                                       new FileDBRegistry(new ApplicationFileManager(applicationDir, new FileDirectory())));

    }

    // For testing only
    FileDistributionProvider(FileRegistry fileRegistry, FileDistribution fileDistribution) {
        this.fileRegistry = fileRegistry;
        this.fileDistribution = fileDistribution;
    }

    public FileRegistry getFileRegistry() {
        return fileRegistry;
    }

    public FileDistribution getFileDistribution() {
        return fileDistribution;
    }

    private void ensureDirExists(File dir) {
        if (!dir.exists()) {
            boolean success = dir.mkdirs();
            if (!success)
                throw new RuntimeException("Could not create directory " + dir.getPath());
        }
    }

}

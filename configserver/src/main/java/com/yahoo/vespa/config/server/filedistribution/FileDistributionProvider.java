// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.vespa.filedistribution.FileDistributionManager;

import java.io.File;
import java.util.concurrent.locks.Lock;

/**
 * Provides file distribution registry and invoker.
 *
 * @author lulf
 * @since 5.1.14
 */
public class FileDistributionProvider {

    private final FileRegistry fileRegistry;
    private final FileDistribution fileDistribution;

    public FileDistributionProvider(File applicationDir, String zooKeepersSpec, String applicationId, Lock fileDistributionLock) {
        ensureDirExists(FileDistribution.getDefaultFileDBPath());
        final FileDistributionManager manager = new FileDistributionManager(
                FileDistribution.getDefaultFileDBPath(),
                applicationDir,
                zooKeepersSpec,
                applicationId,
                fileDistributionLock);
        this.fileDistribution = new FileDBHandler(manager);
        this.fileRegistry = new FileDBRegistry(manager);
    }

    public FileDistributionProvider(FileRegistry fileRegistry, FileDistribution fileDistribution) {
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

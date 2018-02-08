// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.jrt.Supervisor;

import java.io.File;

/**
 * Provides file distribution registry and invoker.
 *
 * @author Ulf Lilleengen
 */
public class FileDistributionProvider {

    private final FileRegistry fileRegistry;
    private final FileDistribution fileDistribution;

    public FileDistributionProvider(Supervisor supervisor, File applicationDir) {
        ensureDirExists(FileDistribution.getDefaultFileDBPath());
        this.fileDistribution = new FileDistributionImpl(supervisor);
        this.fileRegistry = new FileDBRegistry(new ApplicationFileManager(applicationDir, new FileDirectory()));
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

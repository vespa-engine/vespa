// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.application.api.FileRegistry;

import java.io.File;
import java.io.Reader;

/**
 * Provides file distribution registry and invoker.
 *
 * @author Ulf Lilleengen
 */
public class FileDistributionProvider {

    private final FileRegistry fileRegistry;
    private final FileDistribution fileDistribution;
    private final AddFileInterface fileManager;

    public FileDistributionProvider(File applicationDir, FileDistribution fileDistribution) {
        this(new ApplicationFileManager(applicationDir, new FileDirectory(fileDistribution.getFileReferencesDir())), fileDistribution);
        ensureDirExists(fileDistribution.getFileReferencesDir());
    }

    FileDistributionProvider(FileRegistry fileRegistry, FileDistribution fileDistribution) {
       this(null, fileRegistry, fileDistribution);
    }

    private FileDistributionProvider(AddFileInterface fileManager, FileDistribution fileDistribution) {
        this(fileManager, new FileDBRegistry(fileManager), fileDistribution);
    }

    private FileDistributionProvider(AddFileInterface fileManager, FileRegistry fileRegistry, FileDistribution fileDistribution) {
        this.fileManager = fileManager;
        this.fileRegistry = fileRegistry;
        this.fileDistribution = fileDistribution;
    }

    public FileRegistry getFileRegistry() {
        return fileRegistry;
    }

    public FileDistribution getFileDistribution() {
        return fileDistribution;
    }

    public FileRegistry createPregeneratedFileRegistry(Reader persistedState) {
        return FileDBRegistry.create(fileManager, persistedState);
    }

    private static void ensureDirExists(File dir) {
        if (!dir.exists()) {
            boolean success = dir.mkdirs();
            if (!success)
                throw new RuntimeException("Could not create directory " + dir.getPath());
        }
    }

}

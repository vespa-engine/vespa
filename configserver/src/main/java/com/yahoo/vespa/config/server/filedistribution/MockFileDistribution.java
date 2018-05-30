// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;

import java.io.File;
import java.util.Set;

/**
 * @author Ulf Lilleengen
 */
public class MockFileDistribution implements FileDistribution {
    private final File fileReferencesDir;

    MockFileDistribution(File fileReferencesDir) {
        this.fileReferencesDir = fileReferencesDir;
    }

    @Override
    public void startDownload(String hostName, int port, Set<FileReference> fileReferences) {}

    @Override
    public File getFileReferencesDir() { return fileReferencesDir; }

}

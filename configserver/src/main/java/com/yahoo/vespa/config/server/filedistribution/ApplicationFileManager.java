// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import java.io.File;

public class ApplicationFileManager implements AddFileInterface {
    private final File applicationDir;
    private final FileDirectory master;

    ApplicationFileManager(File applicationDir, FileDirectory master) {
        this.applicationDir = applicationDir;
        this.master = master;
    }

    @Override
    public FileReference addFile(String relativePath, FileReference reference) {
        // TODO Wire in when verified in system test
        // return master.addFile(new File(applicationDir, relativePath), reference);
        return reference;
    }

    @Override
    public FileReference addFile(String relativePath) {
        return master.addFile(new File(applicationDir, relativePath));
    }
}

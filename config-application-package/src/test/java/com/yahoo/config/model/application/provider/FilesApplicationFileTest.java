// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.google.common.io.Files;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationFileTest;
import com.yahoo.path.Path;

import java.io.File;

/**
 * @author lulf
 * @since 5.1
 */
public class FilesApplicationFileTest extends ApplicationFileTest {

    @Override
    public ApplicationFile getApplicationFile(Path path) throws Exception {
        File tmp = Files.createTempDir();
        writeAppTo(tmp);
        return new FilesApplicationFile(path, new File(tmp, path.getRelative()));
    }
}

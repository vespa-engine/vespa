// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationFileTest;
import com.yahoo.path.Path;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/**
 * @author Ulf Lilleengen
 */
public class FilesApplicationFileTest extends ApplicationFileTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Override
    public ApplicationFile getApplicationFile(Path path) throws Exception {
        File tmp = temporaryFolder.newFolder();
        writeAppTo(tmp);
        return new FilesApplicationFile(path, new File(tmp, path.getRelative()));
    }
}

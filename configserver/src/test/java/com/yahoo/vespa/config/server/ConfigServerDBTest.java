// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.common.io.Files;
import com.yahoo.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author lulf
 * @since 5.1
 */
public class ConfigServerDBTest {
    private ConfigServerDB serverDB;
    private File tempDir;

    @Before
    public void setup() {
        tempDir = Files.createTempDir();
        serverDB = ConfigServerDB.createTestConfigServerDb(tempDir.getAbsolutePath());
    }

    private ConfigServerDB createInitializer() throws IOException {
        File existingDef = new File(serverDB.classes(), "test.def");
        IOUtils.writeFile(existingDef, "hello", false);
        return ConfigServerDB.createTestConfigServerDb(tempDir.getAbsolutePath());
    }

    @Test
    public void require_that_existing_def_files_are_copied() throws IOException {
        assertThat(serverDB.serverdefs().listFiles().length, is(0));
        createInitializer();
        assertThat(serverDB.serverdefs().listFiles().length, is(1));
    }
}

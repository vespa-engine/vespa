// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class ConfigServerDBTest {
    private ConfigServerDB serverDB;
    private ConfigserverConfig configserverConfig;

    @Before
    public void setup() {
        File dbDir = Files.createTempDir();
        File definitionsDir = Files.createTempDir();
        configserverConfig = new ConfigserverConfig(new ConfigserverConfig.Builder()
                                                            .configServerDBDir(dbDir.getAbsolutePath())
                                                            .configDefinitionsDir(definitionsDir.getAbsolutePath()));
        serverDB = new ConfigServerDB(configserverConfig);
    }

    private void createInitializer() throws IOException {
        File existingDef = new File(serverDB.classes(), "test.def");
        IOUtils.writeFile(existingDef, "hello", false);
        new ConfigServerDB(configserverConfig);
    }

    @Test
    public void require_that_existing_def_files_are_copied() throws IOException {
        assertThat(serverDB.serverdefs().listFiles().length, is(0));
        createInitializer();
        assertThat(serverDB.serverdefs().listFiles().length, is(1));
    }
}

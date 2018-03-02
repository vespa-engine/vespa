// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.foo.SimpletypesConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class ConfigSourceTest {

    @Test(expected = IllegalArgumentException.class)
    public void require_that_FileSource_throws_exception_on_invalid_file() {
        new FileSource(new File("invalid"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_DirSource_throws_exception_on_invalid_dir() {
        new DirSource(new File("invalid"));
    }

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Test
    public void require_that_DirSource_throws_exception_on_missing_file() throws IOException {
        File folder = tmpDir.newFolder();
        DirSource dirSource = new DirSource(folder);
        try {
            ConfigGetter.getConfig(SimpletypesConfig.class, "dir:" + tmpDir, dirSource);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Could not find a config file for '" + SimpletypesConfig.getDefName() + "' in '" + folder + "/'",
                         e.getMessage());
        }
    }

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.cloud.config.ConfigserverConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.15
 */
public class PermanentApplicationPackageTest {
    @Test
    public void testNonexistingApplication() {
        PermanentApplicationPackage permanentApplicationPackage = new PermanentApplicationPackage(
                new ConfigserverConfig(new ConfigserverConfig.Builder().applicationDirectory("_no_such_dir")));
        assertFalse(permanentApplicationPackage.applicationPackage().isPresent());
    }

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testExistingApplication() throws IOException {
        File tmpDir = folder.newFolder();
        PermanentApplicationPackage permanentApplicationPackage = new PermanentApplicationPackage(
                new ConfigserverConfig(new ConfigserverConfig.Builder().applicationDirectory(tmpDir.getAbsolutePath())));
        assertTrue(permanentApplicationPackage.applicationPackage().isPresent());
    }
}

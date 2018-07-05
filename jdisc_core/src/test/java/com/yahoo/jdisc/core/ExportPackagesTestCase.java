// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ExportPackagesTestCase {

    @Test
    public void requireThatPropertiesAreWritten() throws Exception {
        File file = new File("target", ExportPackages.PROPERTIES_FILE);
        file.deleteOnExit();
        ExportPackages.main(new String[] { file.getAbsolutePath() });
        assertTrue(file.exists());
        Properties props = new Properties();
        try (FileReader reader = new FileReader(file)) {
            props.load(reader);
            assertNotNull(props.getProperty(ExportPackages.EXPORT_PACKAGES));
        }
    }
}

package com.yahoo.jdisc.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for {@link ExportPackages}.
 *
 * @author gjoranv
 */
public class ExportPackagesIT {

    @Rule
    public TemporaryFolder tempFolder= new TemporaryFolder();

    @Test
    public void export_packages_are_added_from_dependency_jars() throws Exception {
        File file = tempFolder.newFile(ExportPackages.PROPERTIES_FILE);

        ExportPackages.main(new String[] { file.getAbsolutePath(), "target/dependency/guice-no_aop.jar" });
        assertTrue(file.exists());
        Properties props = new Properties();
        String exportPackages;
        try (FileReader reader = new FileReader(file)) {
            props.load(reader);
            exportPackages = props.getProperty(ExportPackages.EXPORT_PACKAGES);
        }
        assertNotNull(exportPackages);

        assertTrue(exportPackages.contains("com.google.inject"));

    }

}

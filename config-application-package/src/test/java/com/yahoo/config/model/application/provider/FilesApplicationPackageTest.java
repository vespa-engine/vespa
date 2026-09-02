// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provision.zone.ZoneInfo;
import com.yahoo.io.IOUtils;
import com.yahoo.text.Utf8;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class FilesApplicationPackageTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testDeploymentXmlNotAvailable()  {
        File appDir = new File("src/test/resources/multienvapp");
        assertFalse(new File(appDir, "deployment.xml").exists());
        FilesApplicationPackage app = FilesApplicationPackage.fromDir(appDir, Map.of());
        assertFalse(app.getDeployment().isPresent());
        assertTrue(app.getDeploymentSpec().isEmpty());
    }

    @Test
    public void testDeploymentXml() throws IOException {
        File appDir = new File("src/test/resources/app-with-deployment");
        final File deployment = new File(appDir, "deployment.xml");
        assertTrue(deployment.exists());
        FilesApplicationPackage app = FilesApplicationPackage.fromDir(appDir, Map.of());
        assertTrue(app.getDeployment().isPresent());
        assertFalse(app.getDeploymentSpec().isEmpty());
        assertFalse(app.getMajorVersion().isPresent());
        assertEquals(IOUtils.readAll(app.getDeployment().get()), IOUtils.readAll(Utf8.createReader(deployment)));
    }

    @Test
    public void testPinningMajorVersion() throws IOException {
        File appDir = new File("src/test/resources/app-pinning-major-version");
        final File deployment = new File(appDir, "deployment.xml");
        assertTrue(deployment.exists());
        FilesApplicationPackage app = FilesApplicationPackage.fromDir(appDir, Map.of());
        assertTrue(app.getDeployment().isPresent());
        assertTrue(app.getMajorVersion().isPresent());
        assertEquals(6, (int)app.getMajorVersion().get());
        assertEquals(IOUtils.readAll(app.getDeployment().get()), IOUtils.readAll(Utf8.createReader(deployment)));
    }

    @Test
    public void testLegacyOverrides() {
        File appDir = new File("src/test/resources/app-legacy-overrides");
        ApplicationPackage app = FilesApplicationPackage.fromDir(appDir, Map.of());
        var overrides = app.legacyOverrides();
        assertEquals(2, overrides.size());
        assertEquals("something here", overrides.get("foo-bar"));
        assertEquals("false", overrides.get("v7-geo-positions"));
    }

    @Test
    public void failOnMissingServicesXml() throws IOException {
        File appDir = temporaryFolder.newFolder();
        IOUtils.copyDirectory(new File("src/test/resources/multienvapp"), appDir);
        Files.delete(new File(appDir, "services.xml").toPath());
        FilesApplicationPackage app = FilesApplicationPackage.fromDir(appDir, Map.of());
        var exception = assertThrows(IllegalArgumentException.class,
                                     () -> app.preprocess(ZoneInfo.from(new Zone(Environment.dev, RegionName.defaultName())), new BaseDeployLogger()));
        String message = exception.getMessage();
        assertTrue(message.startsWith("services.xml does not exist in application package"));
        assertTrue(message.contains("There are 4 files in the directory"));
    }

    @Test
    public void testValidFileExtensions() {
        File appDir = new File("src/test/resources/app-with-deployment");
        FilesApplicationPackage app = FilesApplicationPackage.fromDir(appDir, Map.of());
        app.validateFileExtensions();
    }

    @Test
    public void testInvalidFileExtensions() {
        File appDir = new File("src/test/resources/app-with-invalid-files-in-subdir");
        FilesApplicationPackage app = FilesApplicationPackage.fromDir(appDir, Map.of());
        try {
            app.validateFileExtensions();
            fail("expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("File in application package with unknown extension: search/query-profiles/file-with-invalid.extension, " +
                                 "please delete or move file to another directory.",
                         e.getMessage());
        }
    }

    @Test
    public void testInvalidFileExtensionInSubDirOfSubDir() {
        File appDir = new File("src/test/resources/app-with-files-with-invalid-extension-in-subdir-of-subdir/");
        FilesApplicationPackage app = FilesApplicationPackage.fromDir(appDir, Map.of());
        try {
            app.validateFileExtensions();
            fail("expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("File in application package with unknown extension: schemas/foo/bar.junk, " +
                                 "please delete or move file to another directory.",
                         e.getMessage());
        }
    }

}

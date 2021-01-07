// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.google.common.io.Files;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.model.application.provider.Bundle;
import com.yahoo.config.model.application.provider.DeployData;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.document.DataType;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.searchdefinition.DocumentOnlySearch;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.search.NamedSchema;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ApplicationDeployTest {

    private static final String TESTDIR = "src/test/cfg/application/";
    private static final String TESTSDDIR = TESTDIR + "app1/searchdefinitions/";

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void testVespaModel() throws SAXException, IOException {
        ApplicationPackageTester tester = ApplicationPackageTester.create(TESTDIR + "app1");
        VespaModel model = new VespaModel(tester.app());
        List<NamedSchema> schemas = tester.getSchemas();
        assertEquals(schemas.size(), 5);
        for (NamedSchema searchDefinition : schemas) {
            Search s = searchDefinition.getSearch();
            switch (s.getName()) {
                case "music":
                case "laptop":
                case "pc":
                case "sock":
                    break;
                case "product":
                    assertTrue(s instanceof DocumentOnlySearch);
                    assertEquals(DataType.STRING, s.getDocument().getField("title").getDataType());
                    break;
                default:
                    fail();
            }
        }
        assertEquals(Set.of(new File(TESTSDDIR + "laptop.sd"),
                            new File(TESTSDDIR + "music.sd"),
                            new File(TESTSDDIR + "pc.sd"),
                            new File(TESTSDDIR + "product.sd"),
                            new File(TESTSDDIR + "sock.sd")),
                     new HashSet<>(tester.app().getSearchDefinitionFiles()));

        List<FilesApplicationPackage.Component> components = tester.app().getComponents();
        assertEquals(1, components.size());
        Map<String, Bundle.DefEntry> defEntriesByName = defEntries2map(components.get(0).getDefEntries());
        assertEquals(5, defEntriesByName.size());

        Bundle.DefEntry def1 = defEntriesByName.get("test-namespace");
        assertNotNull(def1);
        assertEquals("namespace=config\nintVal int default=0", def1.contents);

        Bundle.DefEntry def2 = defEntriesByName.get("namespace-in-filename");
        assertNotNull(def2);
        assertEquals("namespace=a.b\n\ndoubleVal double default=0.0", def2.contents);

        // Check that getFilename works
        ArrayList<String> sdFileNames = new ArrayList<>();
        for (NamedSchema sd : schemas)
            sdFileNames.add(sd.getFilename());
        Collections.sort(sdFileNames);
        assertEquals("laptop.sd", sdFileNames.get(0));
        assertEquals("music.sd", sdFileNames.get(1));
        assertEquals("pc.sd", sdFileNames.get(2));
        assertEquals("product.sd", sdFileNames.get(3));
        assertEquals("sock.sd", sdFileNames.get(4));
    }

    @Test
    public void testGetFile() throws IOException {
        ApplicationPackageTester tester = ApplicationPackageTester.create(TESTDIR + "app1");
        try (Reader foo = tester.app().getFile(Path.fromString("files/foo.json")).createReader()) {
            assertEquals(IOUtils.readAll(foo), "foo : foo\n");
        }
        try (Reader bar = tester.app().getFile(Path.fromString("files/sub/bar.json")).createReader()) {
            assertEquals(IOUtils.readAll(bar), "bar : bar\n");
        }
        assertTrue(tester.app().getFile(Path.createRoot()).exists());
        assertTrue(tester.app().getFile(Path.createRoot()).isDirectory());
    }

    /*
     * Put a list of def entries to a map, with the name as key. This is done because the order
     * of the def entries in the list cannot be guaranteed.
     */
    private Map<String, Bundle.DefEntry> defEntries2map(List<Bundle.DefEntry> defEntries) {
        Map<String, Bundle.DefEntry> ret = new HashMap<>();
        for (Bundle.DefEntry def : defEntries)
            ret.put(def.defName, def);
        return ret;
    }

    @Test
    public void include_dirs_are_included() {
        ApplicationPackageTester tester = ApplicationPackageTester.create(TESTDIR + "include_dirs");

        Set<String> includeDirs = new HashSet<>(tester.app().getUserIncludeDirs());
        assertEquals(Set.of("jdisc_dir", "dir1", "dir2", "empty_dir"), includeDirs);
    }

    @Test
    public void non_existent_include_dir_is_not_allowed() throws Exception {
        File appDir = tmpFolder.newFolder("non-existent-include");
        String services =
                "<services version='1.0'>" +
                "    <include dir='non-existent' />" +
                "</services>\n";

        IOUtils.writeFile(new File(appDir, "services.xml"), services, false);
        try {
            FilesApplicationPackage.fromFile(appDir);
            fail("Expected exception due to non-existent include dir");
        } catch (IllegalArgumentException e) {
            assertEquals("Cannot include directory 'non-existent', as it does not exist. Directory must reside in application package, and path must be given relative to application package.",
                         e.getMessage());
        }
    }

    @Test
    public void testThatModelIsRebuiltWhenSearchDefinitionIsAdded() throws IOException {
        File tmpDir = tmpFolder.getRoot();
        IOUtils.copyDirectory(new File(TESTDIR, "app1"), tmpDir);
        ApplicationPackageTester tester = ApplicationPackageTester.create(tmpDir.getAbsolutePath());
        assertEquals(5, tester.getSchemas().size());
        File sdDir = new File(tmpDir, "schemas");
        File sd = new File(sdDir, "testfoo.sd");
        IOUtils.writeFile(sd, "search testfoo { document testfoo { field bar type string { } } }", false);
        assertEquals(6, tester.getSchemas().size());
    }

    @Test
    public void testThatAppWithDeploymentXmlIsValid() throws IOException {
        File tmpDir = tmpFolder.getRoot();
        IOUtils.copyDirectory(new File(TESTDIR, "app1"), tmpDir);
        ApplicationPackageTester.create(tmpDir.getAbsolutePath());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThatAppWithIllegalDeploymentXmlIsNotValid() throws IOException {
        File tmpDir = tmpFolder.getRoot();
        IOUtils.copyDirectory(new File(TESTDIR, "app_invalid_deployment_xml"), tmpDir);
        ApplicationPackageTester.create(tmpDir.getAbsolutePath());
    }

    @Test
    public void testComplicatedDeploymentSpec() throws IOException {
        File tmpDir = tmpFolder.getRoot();
        IOUtils.copyDirectory(new File(TESTDIR, "app_complicated_deployment_spec"), tmpDir);
        ApplicationPackageTester.create(tmpDir.getAbsolutePath());
    }

    @Test
    public void testAppWithEmptyProdRegion() throws IOException {
        File tmpDir = tmpFolder.getRoot();
        IOUtils.copyDirectory(new File(TESTDIR, "empty_prod_region_in_deployment_xml"), tmpDir);
        ApplicationPackageTester.create(tmpDir.getAbsolutePath());
    }

    @Test
    public void testThatAppWithInvalidParallelDeploymentFails() throws IOException {
        File tmpDir = tmpFolder.getRoot();
        IOUtils.copyDirectory(new File(TESTDIR, "invalid_parallel_deployment_xml"), tmpDir);
        try {
            ApplicationPackageTester.create(tmpDir.getAbsolutePath());
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid XML according to XML schema, error in deployment.xml: element \"instance\" not allowed here; expected the element end-tag or element \"delay\", \"region\", \"steps\" or \"test\" [7:30], input:\n", e.getMessage());
        }
    }

    @Test
    public void testConfigDefinitionsFromJars() {
        String appName = "src/test/cfg//application/app1";
        FilesApplicationPackage app = FilesApplicationPackage.fromFile(new File(appName), false);
        Map<ConfigDefinitionKey, UnparsedConfigDefinition> defs = app.getAllExistingConfigDefs();
        assertEquals(5, defs.size());
    }

    @Test
    public void testMetaData() throws IOException {
        File tmp = Files.createTempDir();
        String appPkg = TESTDIR + "app1";
        IOUtils.copyDirectory(new File(appPkg), tmp);
        ApplicationId applicationId = ApplicationId.from("tenant1", "application1", "instance1");
        DeployData deployData = new DeployData("foo",
                                               "bar",
                                               applicationId,
                                               13L,
                                               false,
                                               1337L,
                                               3L);
        FilesApplicationPackage app = FilesApplicationPackage.fromFileWithDeployData(tmp, deployData);
        app.writeMetaData();
        FilesApplicationPackage newApp = FilesApplicationPackage.fromFileWithDeployData(tmp, deployData);
        ApplicationMetaData meta = newApp.getMetaData();
        assertEquals("foo", meta.getDeployedByUser());
        assertEquals("bar", meta.getDeployPath());
        assertEquals(applicationId, meta.getApplicationId());
        assertEquals(13L, (long)meta.getDeployTimestamp());
        assertEquals(1337L, (long)meta.getGeneration());
        assertEquals(3L, meta.getPreviousActiveGeneration());
        String checksum = meta.getChecksum();
        assertNotNull(checksum);

        assertTrue((new File(tmp, "hosts.xml")).delete());
        FilesApplicationPackage app2 = FilesApplicationPackage.fromFileWithDeployData(tmp, deployData);
        String app2Checksum = app2.getMetaData().getChecksum();
        assertNotEquals(checksum, app2Checksum);

        assertTrue((new File(tmp, "files/foo.json")).delete());
        FilesApplicationPackage app3 = FilesApplicationPackage.fromFileWithDeployData(tmp, deployData);
        String app3Checksum = app3.getMetaData().getChecksum();
        assertNotEquals(app2Checksum, app3Checksum);
    }

    @Test
    public void testGetJarEntryName() {
        JarEntry e = new JarEntry("/schemas/foo.sd");
        assertEquals(ApplicationPackage.getFileName(e), "foo.sd");
        e = new JarEntry("bar");
        assertEquals(ApplicationPackage.getFileName(e), "bar");
        e = new JarEntry("");
        assertEquals(ApplicationPackage.getFileName(e), "");
    }

    @Test
    public void testGetJarEntryNameForLegacyPath() {
        JarEntry e = new JarEntry("/searchdefinitions/foo.sd");
        assertEquals(ApplicationPackage.getFileName(e), "foo.sd");
        e = new JarEntry("bar");
        assertEquals(ApplicationPackage.getFileName(e), "bar");
        e = new JarEntry("");
        assertEquals(ApplicationPackage.getFileName(e), "");
    }

    @After
    public void cleanDirs() {
        IOUtils.recursiveDeleteDir(new File(TESTDIR + "app1/myDir"));
        IOUtils.recursiveDeleteDir(new File(TESTDIR + "app1/searchdefinitions/myDir2"));
        IOUtils.recursiveDeleteDir(new File(TESTDIR + "app1/myDir3"));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @After
    public void cleanFiles() {
        new File(new File(TESTDIR + "app1"),"foo.txt").delete();
        new File(new File(TESTDIR + "app1"),"searchdefinitions/bar.text").delete();
        IOUtils.recursiveDeleteDir(new File(TESTDIR + "app1/mySubDir"));
    }

    /**
     * Tests that an invalid jar is identified as not being a jar file
     */
    @Test
    public void testInvalidJar() {
        try {
            FilesApplicationPackage.getComponents(new File("src/test/cfg/application/validation/invalidjar_app"));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Error opening jar file 'invalid.jar'. Please check that this is a valid jar file",
                         e.getMessage());
        }
    }

    /**
     * Tests that config definitions with namespace are treated properly when they have the format
     * as in the config definitions dir ($VESPA_HOME/share/vespa/configdefinitions on a machine
     * with Vespa packages installed) (does not test when read from user def files). Also tests a config
     * definition without version in file name
     */
    @Test
    public void testConfigDefinitionsAndNamespaces() {
        final File appDir = new File("src/test/cfg/application/configdeftest");
        FilesApplicationPackage app = FilesApplicationPackage.fromFile(appDir);

        DeployState deployState = new DeployState.Builder().applicationPackage(app).build();

        ConfigDefinition def = deployState.getConfigDefinition(new ConfigDefinitionKey("baz", "xyzzy")).get();
        assertEquals("xyzzy", def.getNamespace());

        def = deployState.getConfigDefinition(new ConfigDefinitionKey("foo", "qux")).get();
        assertEquals("qux", def.getNamespace());

        // A config def without version in filename and version in file header
        def = deployState.getConfigDefinition(new ConfigDefinitionKey("bar", "xyzzy")).get();
        assertEquals("xyzzy", def.getNamespace());
        assertEquals("bar", def.getName());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDifferentNameOfSdFileAndSearchName() {
        ApplicationPackageTester tester = ApplicationPackageTester.create(TESTDIR + "sdfilenametest");
        new DeployState.Builder().applicationPackage(tester.app()).build();
    }

}

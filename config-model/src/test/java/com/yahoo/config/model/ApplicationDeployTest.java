// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.google.common.io.Files;
import com.yahoo.component.Version;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.codegen.CNode;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.*;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.path.Path;
import com.yahoo.document.DataType;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.UnproperSearch;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.search.SearchDefinition;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ApplicationDeployTest {

    private static final String TESTDIR = "src/test/cfg/application/";
    private static final String TESTSDDIR = TESTDIR + "app1/searchdefinitions/";

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void testVespaModel() throws SAXException, IOException {
        FilesApplicationPackage app = createAppPkg(TESTDIR + "app1");
        assertThat(app.getApplicationName(), is("app1"));
        VespaModel model = new VespaModel(app);
        List<SearchDefinition> searchDefinitions = getSearchDefinitions(app);
        assertEquals(searchDefinitions.size(), 5);
        for (SearchDefinition searchDefinition : searchDefinitions) {
            Search s = searchDefinition.getSearch();
            switch (s.getName()) {
                case "music":
                case "laptop":
                case "pc":
                case "sock":
                    break;
                case "product":
                    assertTrue(s instanceof UnproperSearch);
                    assertEquals(s.getDocument().getField("title").getDataType(), DataType.STRING);
                    break;
                default:
                    fail();
            }
        }
        File[] truth = new File[]{new File(TESTSDDIR + "laptop.sd"),
                new File(TESTSDDIR + "music.sd"),
                new File(TESTSDDIR + "pc.sd"),
                new File(TESTSDDIR + "product.sd"),
                new File(TESTSDDIR + "sock.sd")};
        Arrays.sort(truth);
        List<File> appSdFiles = app.getSearchDefinitionFiles();
        Collections.sort(appSdFiles);
        assertEquals(appSdFiles, Arrays.asList(truth));

        List<FilesApplicationPackage.Component> components = app.getComponents();
        assertEquals(1, components.size());
        Map<String, Bundle.DefEntry> defEntriesByName =
                defEntries2map(components.get(0).getDefEntries());
        assertEquals(5, defEntriesByName.size());

        Bundle.DefEntry def1 = defEntriesByName.get("test-namespace");
        assertNotNull(def1);
        assertEquals("namespace=config\nintVal int default=0", def1.contents);

        Bundle.DefEntry def2 = defEntriesByName.get("namespace-in-filename");
        assertNotNull(def2);
        assertEquals("namespace=a.b\n\ndoubleVal double default=0.0", def2.contents);

        // Check that getFilename works
        ArrayList<String> sdFileNames = new ArrayList<>();
        for (SearchDefinition sd : searchDefinitions) {
            sdFileNames.add(sd.getFilename());
        }
        Collections.sort(sdFileNames);
        assertThat(sdFileNames.get(0), is("laptop.sd"));
        assertThat(sdFileNames.get(1), is("music.sd"));
        assertThat(sdFileNames.get(2), is("pc.sd"));
        assertThat(sdFileNames.get(3), is("product.sd"));
        assertThat(sdFileNames.get(4), is("sock.sd"));
    }

    @Test
    public void testGetFile() throws IOException {
        FilesApplicationPackage app = createAppPkg(TESTDIR + "app1");
        try (Reader foo = app.getFile(Path.fromString("files/foo.json")).createReader()) {
            assertEquals(IOUtils.readAll(foo), "foo : foo\n");
        }
        try (Reader bar = app.getFile(Path.fromString("files/sub/bar.json")).createReader()) {
            assertEquals(IOUtils.readAll(bar), "bar : bar\n");
        }
        assertTrue(app.getFile(Path.createRoot()).exists());
        assertTrue(app.getFile(Path.createRoot()).isDirectory());
    }

    /*
     * Put a list of def entries to a map, with the name as key. This is done because the order
     * of the def entries in the list cannot be guaranteed.
     */
    private Map<String, Bundle.DefEntry> defEntries2map
    (List<Bundle.DefEntry> defEntries) {
        Map<String, Bundle.DefEntry> ret =
                new HashMap<>();

        for (Bundle.DefEntry def : defEntries)
            ret.put(def.defName, def);
        return ret;
    }

    @Test
    public void testSdFromDocprocBundle() throws IOException, SAXException {
        String appDir = "src/test/cfg/application/app_sdbundles";
        FilesApplicationPackage app = createAppPkg(appDir);
        VespaModel model = new VespaModel(app);
        // Check that the resulting documentmanager config contains those types
        DocumentmanagerConfig.Builder b = new DocumentmanagerConfig.Builder();
        model.getConfig(b, VespaModel.ROOT_CONFIGID);
        //String docMan = model.getConfig("documentmanager", "").toString();
        DocumentmanagerConfig dc = new DocumentmanagerConfig(b);
        String docMan=ConfigInstance.serialize(dc).toString();
        int pFlags = Pattern.MULTILINE + Pattern.DOTALL;
        Pattern base = Pattern.compile(".*name.*base\\.header.*", pFlags);
        Pattern book = Pattern.compile(".*name.*book\\.header.*", pFlags);
        Pattern music = Pattern.compile(".*name.*music\\.header.*", pFlags);
        Pattern video = Pattern.compile(".*name.*video\\.header.*", pFlags);
        Pattern muzak = Pattern.compile(".*name.*muzak\\.header.*", pFlags);
        assertTrue(base.matcher(docMan).matches());
        assertTrue(book.matcher(docMan).matches());
        assertTrue(music.matcher(docMan).matches());
        assertTrue(video.matcher(docMan).matches());
        assertTrue(muzak.matcher(docMan).matches());
    }

    @Test
    public void include_dirs_are_included() throws Exception {
        FilesApplicationPackage app = createAppPkg(TESTDIR + "include_dirs");

        List<String> includeDirs = app.getUserIncludeDirs();
        assertThat(includeDirs, contains("jdisc_dir", "dir1", "dir2", "empty_dir"));
    }

    @Test
    public void non_existent_include_dir_is_not_allowed() throws Exception {
        File appDir = tmpFolder.newFolder("non-existent-include");
        String services = "<services version='1.0'>" +
                "<include dir='non-existent' />" +
                "</services>\n";

        IOUtils.writeFile(new File(appDir, "services.xml"), services, false);
        try {
            FilesApplicationPackage.fromFile(appDir);
            fail("Expected exception due to non-existent include dir");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Cannot include directory 'non-existent', as it does not exist"));
        }
    }

    @Test
    public void testThatModelIsRebuiltWhenSearchDefinitionIsAdded() throws IOException {
        File tmpDir = tmpFolder.getRoot();
        IOUtils.copyDirectory(new File(TESTDIR, "app1"), tmpDir);
        FilesApplicationPackage app = createAppPkg(tmpDir.getAbsolutePath());
        assertThat(getSearchDefinitions(app).size(), is(5));
        File sdDir = new File(tmpDir, "searchdefinitions");
        File sd = new File(sdDir, "testfoo.sd");
        IOUtils.writeFile(sd, "search testfoo { document testfoo { field bar type string { } } }", false);
        assertThat(getSearchDefinitions(app).size(), is(6));
    }

    @Test
    public void testThatAppWithDeploymentXmlIsValid() throws IOException {
        File tmpDir = tmpFolder.getRoot();
        IOUtils.copyDirectory(new File(TESTDIR, "app1"), tmpDir);
        createAppPkg(tmpDir.getAbsolutePath());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThatAppWithIllegalDeploymentXmlIsNotValid() throws IOException {
        File tmpDir = tmpFolder.getRoot();
        IOUtils.copyDirectory(new File(TESTDIR, "app_invalid_deployment_xml"), tmpDir);
        createAppPkg(tmpDir.getAbsolutePath());
    }

    @Test
    public void testThatAppWithIllegalEmptyProdRegion() throws IOException {
        File tmpDir = tmpFolder.getRoot();
        IOUtils.copyDirectory(new File(TESTDIR, "empty_prod_region_in_deployment_xml"), tmpDir);
        createAppPkg(tmpDir.getAbsolutePath());
    }

    @Test
    public void testThatAppWithInvalidParallelDeploymentFails() throws IOException {
        File tmpDir = tmpFolder.getRoot();
        IOUtils.copyDirectory(new File(TESTDIR, "invalid_parallel_deployment_xml"), tmpDir);
        try {
            createAppPkg(tmpDir.getAbsolutePath());
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("element \"delay\" not allowed here"));
        }
    }

    private List<SearchDefinition> getSearchDefinitions(FilesApplicationPackage app) {
        return new DeployState.Builder().applicationPackage(app).build(true).getSearchDefinitions();
    }

    public FilesApplicationPackage createAppPkg(String appPkg) throws IOException {
        return createAppPkg(appPkg, true);
    }

    public FilesApplicationPackage createAppPkgDoNotValidateXml(String appPkg) throws IOException {
        return createAppPkg(appPkg, false);
    }

    public FilesApplicationPackage createAppPkg(String appPkg, boolean validateXml) throws IOException {
        final FilesApplicationPackage filesApplicationPackage = FilesApplicationPackage.fromFile(new File(appPkg));
        if (validateXml) {
            ApplicationPackageXmlFilesValidator validator =
                    ApplicationPackageXmlFilesValidator.create(new File(appPkg), new Version(6));
            validator.checkApplication();
            validator.checkIncludedDirs(filesApplicationPackage);
        }
        return filesApplicationPackage;
    }

    @Test
    public void testThatNewServicesFileNameWorks() throws IOException {
    	String appPkg = TESTDIR + "newfilenames";
        assertEquals(appPkg + "/services.xml", createAppPkgDoNotValidateXml(appPkg).getServicesSource());
    }

    @Test
    public void testThatNewHostsFileNameWorks() throws IOException {
    	String appPkg = TESTDIR + "newfilenames";
        assertEquals(appPkg + "/hosts.xml", createAppPkgDoNotValidateXml(appPkg).getHostSource());
    }

    @Test
    public void testGetJars() throws IOException {
        String jarName = "src/test/cfg/application/app_sdbundles/components/testbundle.jar";
        JarFile jar = new JarFile(jarName);
        Map<String, String> payloads = ApplicationPackage.getBundleSdFiles("", jar);
        assertEquals(payloads.size(), 4);
        assertTrue(payloads.get("base.sd").startsWith("search base"));
        assertTrue(payloads.get("book.sd").startsWith("search book"));
        assertTrue(payloads.get("music.sd").startsWith("search music"));
        assertTrue(payloads.get("video.sd").startsWith("search video"));
        assertTrue(payloads.get("base.sd").endsWith("}"));
        assertTrue(payloads.get("book.sd").endsWith("}\n"));
        assertTrue(payloads.get("music.sd").endsWith("}\n"));
        assertTrue(payloads.get("video.sd").endsWith("}\n"));
    }

    @Test
    public void testConfigDefinitionsFromJars() {
        String appName = "src/test/cfg//application/app1";
        FilesApplicationPackage app = FilesApplicationPackage.fromFile(new File(appName), false);
        Map<ConfigDefinitionKey, UnparsedConfigDefinition> defs = app.getAllExistingConfigDefs();
        assertThat(defs.size(), is(5));
    }

    @Test
    public void testMetaData() throws IOException {
        File tmp = Files.createTempDir();
        String appPkg = TESTDIR + "app1";
        IOUtils.copyDirectory(new File(appPkg), tmp);
        DeployData deployData = new DeployData("foo", "bar", "baz", 13l, false, 1337l, 3l);
        FilesApplicationPackage app = FilesApplicationPackage.fromFileWithDeployData(tmp, deployData);
        app.writeMetaData();
        FilesApplicationPackage newApp = FilesApplicationPackage.fromFileWithDeployData(tmp, deployData);
        ApplicationMetaData meta = newApp.getMetaData();
        assertThat(meta.getDeployedByUser(), is("foo"));
        assertThat(meta.getDeployPath(), is("bar"));
        assertThat(meta.getDeployTimestamp(), is(13L));
        assertThat(meta.getGeneration(), is(1337L));
        assertThat(meta.getPreviousActiveGeneration(), is(3L));
        String checkSum = meta.getCheckSum();
        assertNotNull(checkSum);

        assertTrue((new File(tmp, "hosts.xml")).delete());
        FilesApplicationPackage app2 = FilesApplicationPackage.fromFileWithDeployData(tmp, deployData);
        String app2CheckSum = app2.getMetaData().getCheckSum();
        assertThat(app2CheckSum, is(not(checkSum)));

        assertTrue((new File(tmp, "files/foo.json")).delete());
        FilesApplicationPackage app3 = FilesApplicationPackage.fromFileWithDeployData(tmp, deployData);
        String app3CheckSum = app3.getMetaData().getCheckSum();
        assertThat(app3CheckSum, is(not(app2CheckSum)));
    }

    @Test
    public void testGetJarEntryName() {
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
            assertThat(e.getMessage(), is("Error opening jar file 'invalid.jar'. Please check that this is a valid jar file"));
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

        DeployState deployState = new DeployState.Builder().applicationPackage(app).build(true);

        ConfigDefinition def = deployState.getConfigDefinition(new ConfigDefinitionKey("foo", CNode.DEFAULT_NAMESPACE)).get();
        assertThat(def.getNamespace(), is(CNode.DEFAULT_NAMESPACE));

        def = deployState.getConfigDefinition(new ConfigDefinitionKey("baz", "xyzzy")).get();
        assertThat(def.getNamespace(), is("xyzzy"));

        def = deployState.getConfigDefinition(new ConfigDefinitionKey("foo", "qux")).get();
        assertThat(def.getNamespace(), is("qux"));

        // A config def without version in filename and version in file header
        def = deployState.getConfigDefinition(new ConfigDefinitionKey("xyzzy", CNode.DEFAULT_NAMESPACE)).get();
        assertThat(def.getNamespace(), is(CNode.DEFAULT_NAMESPACE));
        assertThat(def.getName(), is("xyzzy"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDifferentNameOfSdFileAndSearchName() throws IOException {
        FilesApplicationPackage app = createAppPkg(TESTDIR + "sdfilenametest");
        new DeployState.Builder().applicationPackage(app).build(true);
    }

}

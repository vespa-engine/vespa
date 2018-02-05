// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import com.yahoo.vespa.configmodel.producers.DocumentTypes;

import java.io.*;

/**
 * Superclass of tests needing file comparisons
 *
 * @author bratseth
 */
public abstract class AbstractExportingTestCase extends SearchDefinitionTestCase {

    static final String tempDir = "temp/";
    static final String searchDefRoot = "src/test/derived/";

    private static final boolean WRITE_FILES = false;

    static {
        if ("true".equals(System.getProperty("sd.updatetests"))) {
            /*
             * Use this when you know that your code is correct, and don't want to manually check and fix the .cfg files
             * in the exporting tests.
             */
            setUpUpdateTest();
        }
        // Or uncomment the lines you want below AND set WRITE_FILES to true
        //System.setProperty("sd.updatetests", "true");
        //System.setProperty("sd.updatetestfile", "attributes");
        //System.setProperty("sd.updatetestfile", "documentmanager");
        //System.setProperty("sd.updatetestfile", "fdispatchrc");
        //System.setProperty("sd.updatetestfile", "ilscripts");
        //System.setProperty("sd.updatetestfile", "index-info");
        //System.setProperty("sd.updatetestfile", "indexschema");
        //System.setProperty("sd.updatetestfile", "juniperrc");
        //System.setProperty("sd.updatetestfile", "partitions");
        //System.setProperty("sd.updatetestfile", "qr-logging");
        //System.setProperty("sd.updatetestfile", "qr-searchers");
        //System.setProperty("sd.updatetestfile", "rank-profiles");
        //System.setProperty("sd.updatetestfile", "summary");
        //System.setProperty("sd.updatetestfile", "summarymap");
        //System.setProperty("sd.updatetestfile", "translogserver");
        //System.setProperty("sd.updatetestfile", "vsmsummary");
        //System.setProperty("sd.updatetestfile", "vsmfields");
    }

    private static void setUpUpdateTest() {
        try {
            System.out.println("Property sd.updatetests is true, updating test files from generated ones...");
            System.out.println("Enter export test file name to be updated (eg. index-info), or blank to update every file. 'q' to quit: ");
            String fileName = new BufferedReader(new InputStreamReader(System.in)).readLine();
            if ("q".equals(fileName)) {
                throw new IllegalArgumentException("Aborted by user");
            }
            System.setProperty("sd.updatetestfile", fileName);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected DerivedConfiguration derive(String dirName, String searchDefinitionName) throws IOException, ParseException {
        File toDir = new File(tempDir + dirName);
        toDir.mkdirs();
        deleteContent(toDir);

        SearchBuilder builder = SearchBuilder.createFromDirectory(searchDefRoot + dirName + "/");
        //SearchBuilder builder = SearchBuilder.createFromFile(searchDefDir + name + ".sd");
        return derive(dirName, searchDefinitionName, builder);
    }

    protected DerivedConfiguration derive(String dirName, String searchDefinitionName, SearchBuilder builder) throws IOException {
        DerivedConfiguration config = new DerivedConfiguration(builder.getSearch(searchDefinitionName),
                                                               builder.getRankProfileRegistry(),
                                                               builder.getQueryProfileRegistry());
        return export(dirName, builder, config);
    }

    protected DerivedConfiguration derive(String dirName, SearchBuilder builder, Search search) throws IOException {
        DerivedConfiguration config = new DerivedConfiguration(search,
                                                               builder.getRankProfileRegistry(),
                                                               builder.getQueryProfileRegistry());
        return export(dirName, builder, config);
    }

    private DerivedConfiguration export(String name, SearchBuilder builder, DerivedConfiguration config) throws IOException {
        String path = exportConfig(name, config);
        DerivedConfiguration.exportDocuments(new DocumentManager().produce(builder.getModel(), new DocumentmanagerConfig.Builder()), path);
        DerivedConfiguration.exportDocuments(new DocumentTypes().produce(builder.getModel(), new DocumenttypesConfig.Builder()), path);
        return config;
    }

    private String exportConfig(String name, DerivedConfiguration config) throws IOException {
        String path = tempDir + name;
        config.export(path);
        return path;
    }

    /**
     * Derives a config from name/name.sd below the test dir and verifies that every .cfg file in name/ has a
     * corresponding file with the same content in temp/name. Versions can and should be omitted from the .cfg file
     * names. This will fail if the search definition dir has multiple search definitions.
     *
     * @param dirName the name of the directory containing the searchdef file to verify.
     * @throws ParseException if the .sd file could not be parsed.
     * @throws IOException    if file access failed.
     */
    protected DerivedConfiguration assertCorrectDeriving(String dirName) throws IOException, ParseException {
        return assertCorrectDeriving(dirName, null);
    }

    protected DerivedConfiguration assertCorrectDeriving(String dirName, String searchDefinitionName) throws IOException, ParseException {
        DerivedConfiguration derived = derive(dirName, searchDefinitionName);
        assertCorrectConfigFiles(dirName);
        return derived;
    }

    /**
     * Asserts config is correctly derived given a builder.
     * This will fail if the builder contains multiple search definitions.
     */
    protected DerivedConfiguration assertCorrectDeriving(SearchBuilder builder, String dirName) throws IOException, ParseException {
        builder.build();
        DerivedConfiguration derived = derive(dirName, null, builder);
        assertCorrectConfigFiles(dirName);
        return derived;
    }

    protected DerivedConfiguration assertCorrectDeriving(SearchBuilder builder, Search search, String name) throws IOException, ParseException {
        DerivedConfiguration derived = derive(name, builder, search);
        assertCorrectConfigFiles(name);
        return derived;
    }

    /**
     * Assert that search is derived into the files in the directory given by name.
     *
     * @param name the local name of the directory containing the files to check
     * @throws IOException If file access failed.
     */
    protected void assertCorrectConfigFiles(String name) throws IOException {
        File[] files = new File(searchDefRoot, name).listFiles();
        if (files == null) return;
        for (File file : files) {
            if ( ! file.getName().endsWith(".cfg")) continue;
            assertEqualFiles(file.getPath(), tempDir + name + "/" + file.getName());
        }
    }

    protected void assertEqualConfig(String name, String config) throws IOException {
        final String expectedConfigDirName = searchDefRoot + name + "/";
        assertEqualFiles(expectedConfigDirName + config + ".cfg",
                         tempDir + name + "/" + config + ".cfg");
    }

    @SuppressWarnings({ "ConstantConditions" })
    public static void assertEqualFiles(String correctFileName, String checkFileName) throws IOException {
        if (WRITE_FILES || "true".equals(System.getProperty("sd.updatetests"))) {
            String updateFile = System.getProperty("sd.updatetestfile");
            if (WRITE_FILES || "".equals(updateFile) || correctFileName.endsWith(updateFile + ".cfg") || correctFileName.endsWith(updateFile + ".MODEL.cfg")) {
                System.out.println("Copying " + checkFileName + " to " + correctFileName);
                IOUtils.copy(checkFileName, correctFileName);
                return;
            }
        }
        assertConfigFiles(correctFileName, checkFileName);
    }

    protected void deleteContent(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            file.delete();
        }
    }

}

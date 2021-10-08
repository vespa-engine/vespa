// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.concurrent.InThreadExecutorService;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.document.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SchemaTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import com.yahoo.vespa.configmodel.producers.DocumentTypes;

import java.io.File;
import java.io.IOException;

/**
 * Superclass of tests needing file comparisons
 *
 * @author bratseth
 */
public abstract class AbstractExportingTestCase extends SchemaTestCase {

    private static final String tempDir = "temp/";
    private static final String searchDefRoot = "src/test/derived/";

    private DerivedConfiguration derive(String dirName,
                                        String searchDefinitionName,
                                        TestProperties properties,
                                        DeployLogger logger) throws IOException, ParseException {
        File toDir = new File(tempDir + dirName);
        toDir.mkdirs();
        deleteContent(toDir);

        SearchBuilder builder = SearchBuilder.createFromDirectory(searchDefRoot + dirName + "/", new MockFileRegistry(), logger, properties);
        return derive(dirName, searchDefinitionName, properties, builder, logger);
    }

    private DerivedConfiguration derive(String dirName,
                                        String searchDefinitionName,
                                        TestProperties properties,
                                        SearchBuilder builder,
                                        DeployLogger logger) throws IOException {
        DerivedConfiguration config = new DerivedConfiguration(builder.getSearch(searchDefinitionName),
                                                               logger,
                                                               properties,
                                                               builder.getRankProfileRegistry(),
                                                               builder.getQueryProfileRegistry(),
                                                               new ImportedMlModels(), new InThreadExecutorService());
        return export(dirName, builder, config);
    }

    DerivedConfiguration derive(String dirName, SearchBuilder builder, Search search) throws IOException {
        DerivedConfiguration config = new DerivedConfiguration(search,
                                                               builder.getRankProfileRegistry(),
                                                               builder.getQueryProfileRegistry());
        return export(dirName, builder, config);
    }

    private DerivedConfiguration export(String name, SearchBuilder builder, DerivedConfiguration config) throws IOException {
        String path = exportConfig(name, config);
        DerivedConfiguration.exportDocuments(new DocumentManager().produce(builder.getModel(), new DocumentmanagerConfig.Builder()), path);
        DerivedConfiguration.exportDocuments(new DocumentTypes().produce(builder.getModel(), new DocumenttypesConfig.Builder()), path);
        DerivedConfiguration.exportQueryProfiles(builder.getQueryProfileRegistry(), path);
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
        return assertCorrectDeriving(dirName, new TestableDeployLogger());
    }
    protected DerivedConfiguration assertCorrectDeriving(String dirName, DeployLogger logger) throws IOException, ParseException {
        return assertCorrectDeriving(dirName, null, logger);
    }

    protected DerivedConfiguration assertCorrectDeriving(String dirName,
                                                         String searchDefinitionName,
                                                         DeployLogger logger) throws IOException, ParseException {
        return assertCorrectDeriving(dirName, searchDefinitionName, new TestProperties(), logger);
    }

    protected DerivedConfiguration assertCorrectDeriving(String dirName,
                                                         String searchDefinitionName,
                                                         TestProperties properties,
                                                         DeployLogger logger) throws IOException, ParseException {
        DerivedConfiguration derived = derive(dirName, searchDefinitionName, properties, logger);
        assertCorrectConfigFiles(dirName);
        return derived;
    }

    /**
     * Asserts config is correctly derived given a builder.
     * This will fail if the builder contains multiple search definitions.
     */
    protected DerivedConfiguration assertCorrectDeriving(SearchBuilder builder, String dirName, DeployLogger logger) throws IOException {
        builder.build();
        DerivedConfiguration derived = derive(dirName, null, new TestProperties(), builder, logger);
        assertCorrectConfigFiles(dirName);
        return derived;
    }

    protected DerivedConfiguration assertCorrectDeriving(SearchBuilder builder, Search search, String name) throws IOException {
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
    void assertCorrectConfigFiles(String name) throws IOException {
        File[] files = new File(searchDefRoot, name).listFiles();
        if (files == null) return;
        for (File file : files) {
            if ( ! file.getName().endsWith(".cfg")) continue;
            boolean orderMatters = file.getName().equals("ilscripts.cfg");
            assertEqualFiles(file.getPath(), tempDir + name + "/" + file.getName(), orderMatters);
        }
    }

    static void assertEqualFiles(String correctFileName, String checkFileName, boolean orderMatters) throws IOException {
        // Set updateOnAssert to true if you want update the files with correct answer.
        assertConfigFiles(correctFileName, checkFileName, orderMatters, false);
    }

    void deleteContent(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            file.delete();
        }
    }

}

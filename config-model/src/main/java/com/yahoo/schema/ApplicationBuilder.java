// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileXMLReader;
import com.yahoo.schema.parser.ConvertSchemaCollection;
import com.yahoo.schema.parser.IntermediateCollection;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.schema.processing.Processor;
import com.yahoo.vespa.documentmodel.DocumentModel;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Application builder. Usage:
 * 1) Add all schemas, using the addXXX() methods,
 * 2) provide the available rank types and ranking expressions, using the setRankXXX() methods,
 * 3) invoke the {@link #build} method
 *
 * @author bratseth
 */
public class ApplicationBuilder {

    private final IntermediateCollection mediator;
    private final ApplicationPackage applicationPackage;
    private final List<Schema> schemas = new ArrayList<>();
    private final DocumentTypeManager documentTypeManager = new DocumentTypeManager();
    private final RankProfileRegistry rankProfileRegistry;
    private final QueryProfileRegistry queryProfileRegistry;
    private final FileRegistry fileRegistry;
    private final DeployLogger deployLogger;
    private final ModelContext.Properties properties;
    /** True to build the document aspect only, skipping instantiation of rank profiles */
    private final boolean documentsOnly;

    private Application application;

    private final Set<Class<? extends Processor>> processorsToSkip = new HashSet<>();

    /** For testing only */
    public ApplicationBuilder() {
        this(new RankProfileRegistry(), new QueryProfileRegistry());
    }

    /** For testing only */
    public ApplicationBuilder(DeployLogger deployLogger) {
        this(MockApplicationPackage.createEmpty(), deployLogger);
    }

    /** For testing only */
    public ApplicationBuilder(DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry) {
        this(MockApplicationPackage.createEmpty(), deployLogger, rankProfileRegistry);
    }

    /** Used for generating documents for typed access to document fields in Java */
    public ApplicationBuilder(boolean documentsOnly) {
        this(MockApplicationPackage.createEmpty(), new MockFileRegistry(), new BaseDeployLogger(), new TestProperties(), new RankProfileRegistry(), new QueryProfileRegistry(), documentsOnly);
    }

    /** For testing only */
    public ApplicationBuilder(ApplicationPackage app, DeployLogger deployLogger) {
        this(app, new MockFileRegistry(), deployLogger, new TestProperties(), new RankProfileRegistry(), new QueryProfileRegistry());
    }

    /** For testing only */
    public ApplicationBuilder(ApplicationPackage app, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry) {
        this(app, new MockFileRegistry(), deployLogger, new TestProperties(), rankProfileRegistry, new QueryProfileRegistry());
    }

    /** For testing only */
    public ApplicationBuilder(RankProfileRegistry rankProfileRegistry) {
        this(rankProfileRegistry, new QueryProfileRegistry());
    }

    /** For testing only */
    public ApplicationBuilder(RankProfileRegistry rankProfileRegistry, QueryProfileRegistry queryProfileRegistry) {
        this(rankProfileRegistry, queryProfileRegistry, new TestProperties());
    }

    /** For testing only */
    public ApplicationBuilder(ModelContext.Properties properties) {
        this(new RankProfileRegistry(), new QueryProfileRegistry(), properties);
    }

    /** For testing only */
    public ApplicationBuilder(RankProfileRegistry rankProfileRegistry, QueryProfileRegistry queryProfileRegistry, ModelContext.Properties properties) {
        this(MockApplicationPackage.createEmpty(), new MockFileRegistry(), new BaseDeployLogger(), properties, rankProfileRegistry, queryProfileRegistry);
    }

    /** Regular constructor */
    public ApplicationBuilder(ApplicationPackage app,
                              FileRegistry fileRegistry,
                              DeployLogger deployLogger,
                              ModelContext.Properties properties,
                              RankProfileRegistry rankProfileRegistry,
                              QueryProfileRegistry queryProfileRegistry) {
        this(app, fileRegistry, deployLogger, properties, rankProfileRegistry, queryProfileRegistry, false);
    }

    private ApplicationBuilder(ApplicationPackage applicationPackage,
                               FileRegistry fileRegistry,
                               DeployLogger deployLogger,
                               ModelContext.Properties properties,
                               RankProfileRegistry rankProfileRegistry,
                               QueryProfileRegistry queryProfileRegistry,
                               boolean documentsOnly) {
        this.mediator = new IntermediateCollection(deployLogger, properties);
        this.applicationPackage = applicationPackage;
        this.rankProfileRegistry = rankProfileRegistry;
        this.queryProfileRegistry = queryProfileRegistry;
        this.fileRegistry = fileRegistry;
        this.deployLogger = deployLogger;
        this.properties = properties;
        this.documentsOnly = documentsOnly;
        var list = new ArrayList<>(applicationPackage.getSchemas());
        list.sort((a, b) -> a.getName().compareTo(b.getName()));
        for (NamedReader reader : list) {
            addSchema(reader);
        }
    }

    /**
     * Adds a schema to this application.
     *
     * @param fileName the name of the file to import
     * @throws IOException    thrown if the file can not be read for some reason
     * @throws ParseException thrown if the file does not contain a valid search definition
     */
    public void addSchemaFile(String fileName) throws IOException, ParseException {
        var parsedName = mediator.addSchemaFromFile(fileName);
        addRankProfileFiles(parsedName);
    }

    /**
     * Reads and parses the schema string provided by the given reader. Once all schemas have been
     * imported, call {@link #build}.
     *
     * @param reader the reader whose content to import
     */
    public void addSchema(NamedReader reader) {
        try {
            var parsedName = mediator.addSchemaFromReader(reader);
            addRankProfileFiles(parsedName);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Could not parse schema file '" + reader.getName() + "'", e);
        }
    }

    /**
     * Adds a schema to this
     *
     * @param schemaString the content of the schema
     */
    public void addSchema(String schemaString) throws ParseException {
        var parsed = mediator.addSchemaFromString(schemaString);
        addRankProfileFiles(parsed.name());
    }

    /**
     * Registers the given schema to the application to be built during {@link #build}. A
     * {@link Schema} object is considered to be "raw" if it has not already been processed. This is the case for most
     * programmatically constructed schemas used in unit tests.
     *
     * @param schema the object to import
     * @throws IllegalArgumentException if the given search object has already been processed
     */
    public Schema add(Schema schema) {
        if (schema.getName() == null)
            throw new IllegalArgumentException("Schema has no name");
        schemas.add(schema);
        return schema;
    }

    private void addRankProfileFiles(String schemaName) throws ParseException {
        if (applicationPackage == null) return;

        Path legacyRankProfilePath = ApplicationPackage.SEARCH_DEFINITIONS_DIR.append(schemaName);
        for (NamedReader reader : applicationPackage.getFiles(legacyRankProfilePath, ".profile")) {
            mediator.addRankProfileFile(schemaName, reader);
        }

        Path rankProfilePath = ApplicationPackage.SCHEMAS_DIR.append(schemaName);
        for (NamedReader reader : applicationPackage.getFiles(rankProfilePath, ".profile", true)) {
            mediator.addRankProfileFile(schemaName, reader);
        }
    }

    /**
     * Processes and finalizes the schemas of this.
     *
     * @throws IllegalStateException thrown if this method has already been called
     */
    public Application build(boolean validate) {
        if (application != null) throw new IllegalStateException("Application already built");
        var converter = new ConvertSchemaCollection(mediator,
                                                    documentTypeManager,
                                                    applicationPackage,
                                                    fileRegistry,
                                                    deployLogger,
                                                    properties,
                                                    rankProfileRegistry,
                                                    documentsOnly);
        for (var schema : converter.convertToSchemas())
            add(schema);
        application = new Application(applicationPackage,
                                      schemas,
                                      rankProfileRegistry,
                                      new QueryProfiles(queryProfileRegistry, deployLogger),
                                      properties,
                                      documentsOnly,
                                      validate,
                                      processorsToSkip,
                                      deployLogger);
        return application;
    }

    /** Returns a modifiable set of processors we should skip for these schemas. Useful for testing. */
    public Set<Class<? extends Processor>> processorsToSkip() { return processorsToSkip; }

    /**
     * Convenience method to call {@link #getSchema(String)} when there is only a single {@link Schema} object
     * built. This method will never return null.
     *
     * @return the built object
     * @throws IllegalStateException if there is not exactly one search.
     */
    public Schema getSchema() {
        if (application == null)  throw new IllegalStateException("Application not built");
        if (application.schemas().size() != 1)
            throw new IllegalStateException("This call only works if we have 1 schema. Schemas: " +
                                            application.schemas().values());

        return application.schemas().values().stream().findAny().get();
    }

    public DocumentModel getModel() { return application.documentModel(); }

    /**
     * Returns the built {@link Schema} object that has the given name. If the name is unknown, this method will simply
     * return null.
     *
     * @param name the name of the schema to return,
     *             or null to return the only one or throw an exception if there are multiple to choose from
     * @return the built object, or null if none with this name
     * @throws IllegalStateException if {@link #build} has not been called.
     */
    public Schema getSchema(String name) {
        if (application == null)  throw new IllegalStateException("Application not built");
        if (name == null) return getSchema();
        return application.schemas().get(name);
    }

    public Application application() { return application; }

    /**
     * Convenience method to return a list of all built {@link Schema} objects.
     *
     * @return the list of built searches
     */
    public List<Schema> getSchemaList() {
        return new ArrayList<>(application.schemas().values());
    }

    /**
     * Convenience factory method to import and build a {@link Schema} object from a string.
     *
     * @param sd the string to build from
     * @return the built {@link ApplicationBuilder} object
     * @throws ParseException thrown if there is a problem parsing the string
     */
    public static ApplicationBuilder createFromString(String sd) throws ParseException {
        return createFromString(sd, new BaseDeployLogger());
    }

    public static ApplicationBuilder createFromString(String sd, DeployLogger logger) throws ParseException {
        ApplicationBuilder builder = new ApplicationBuilder(logger);
        builder.addSchema(sd);
        builder.build(true);
        return builder;
    }

    public static ApplicationBuilder createFromStrings(DeployLogger logger, String ... schemas) throws ParseException {
        ApplicationBuilder builder = new ApplicationBuilder(logger);
        for (var schema : schemas)
            builder.addSchema(schema);
        builder.build(true);
        return builder;
    }

    /**
     * Convenience factory method to import and build a {@link Schema} object from a file. Only for testing.
     *
     * @param fileName the file to build from
     * @return the built {@link ApplicationBuilder} object
     * @throws IOException    if there was a problem reading the file.
     * @throws ParseException if there was a problem parsing the file content.
     */
    public static ApplicationBuilder createFromFile(String fileName) throws IOException, ParseException {
        return createFromFile(fileName, new BaseDeployLogger());
    }

    /**
     * Convenience factory methods to create a SearchBuilder from multiple SD files. Only for testing.
     */
    public static ApplicationBuilder createFromFiles(Collection<String> fileNames) throws IOException, ParseException {
        return createFromFiles(fileNames, new BaseDeployLogger());
    }

    public static ApplicationBuilder createFromFile(String fileName, DeployLogger logger) throws IOException, ParseException {
        return createFromFile(fileName, logger, new RankProfileRegistry(), new QueryProfileRegistry());
    }

    private static ApplicationBuilder createFromFiles(Collection<String> fileNames, DeployLogger logger) throws IOException, ParseException {
        return createFromFiles(fileNames, new MockFileRegistry(), logger, new TestProperties(), new RankProfileRegistry(), new QueryProfileRegistry());
    }

    /**
     * Convenience factory method to import and build a {@link Schema} object from a file.
     *
     * @param fileName the file to build from.
     * @param deployLogger logger for deploy messages.
     * @param rankProfileRegistry registry for rank profiles.
     * @return the built {@link ApplicationBuilder} object.
     * @throws IOException    if there was a problem reading the file.
     * @throws ParseException if there was a problem parsing the file content.
     */
    private static ApplicationBuilder createFromFile(String fileName,
                                                     DeployLogger deployLogger,
                                                     RankProfileRegistry rankProfileRegistry,
                                                     QueryProfileRegistry queryprofileRegistry)
            throws IOException, ParseException {
        return createFromFiles(Collections.singletonList(fileName), new MockFileRegistry(), deployLogger, new TestProperties(),
                               rankProfileRegistry, queryprofileRegistry);
    }

    /**
     * Convenience factory methdd to create a SearchBuilder from multiple SD files..
     */
    private static ApplicationBuilder createFromFiles(Collection<String> fileNames,
                                                      FileRegistry fileRegistry,
                                                      DeployLogger deployLogger,
                                                      ModelContext.Properties properties,
                                                      RankProfileRegistry rankProfileRegistry,
                                                      QueryProfileRegistry queryprofileRegistry)
            throws IOException, ParseException {
        ApplicationBuilder builder = new ApplicationBuilder(MockApplicationPackage.createEmpty(),
                                                            fileRegistry,
                                                            deployLogger,
                                                            properties,
                                                            rankProfileRegistry,
                                                            queryprofileRegistry);
        for (String fileName : fileNames) {
            builder.addSchemaFile(fileName);
        }
        builder.build(true);
        return builder;
    }


    public static ApplicationBuilder createFromDirectory(String dir, FileRegistry fileRegistry, DeployLogger logger, ModelContext.Properties properties) throws IOException, ParseException {
        return createFromDirectory(dir, fileRegistry, logger, properties, new RankProfileRegistry());
    }
    public static ApplicationBuilder createFromDirectory(String dir,
                                                         FileRegistry fileRegistry,
                                                         DeployLogger logger,
                                                         ModelContext.Properties properties,
                                                         RankProfileRegistry rankProfileRegistry) throws IOException, ParseException {
        return createFromDirectory(dir, fileRegistry, logger, properties, rankProfileRegistry, createQueryProfileRegistryFromDirectory(dir));
    }
    private static ApplicationBuilder createFromDirectory(String dir,
                                                          FileRegistry fileRegistry,
                                                          DeployLogger logger,
                                                          ModelContext.Properties properties,
                                                          RankProfileRegistry rankProfileRegistry,
                                                          QueryProfileRegistry queryProfileRegistry) throws IOException, ParseException {
        return createFromDirectory(dir, MockApplicationPackage.fromSearchDefinitionAndRootDirectory(dir), fileRegistry, logger, properties,
                                   rankProfileRegistry, queryProfileRegistry);
    }

    private static ApplicationBuilder createFromDirectory(String dir,
                                                          ApplicationPackage applicationPackage,
                                                          FileRegistry fileRegistry,
                                                          DeployLogger deployLogger,
                                                          ModelContext.Properties properties,
                                                          RankProfileRegistry rankProfileRegistry,
                                                          QueryProfileRegistry queryProfileRegistry) throws IOException, ParseException {
        ApplicationBuilder builder = new ApplicationBuilder(applicationPackage,
                                                            fileRegistry,
                                                            deployLogger,
                                                            properties,
                                                            rankProfileRegistry,
                                                            queryProfileRegistry);

        var fnli = Files.list(new File(dir).toPath())
            .map(p -> p.toString())
            .filter(fn -> fn.endsWith(".sd"))
            .sorted();
        for (var i = fnli.iterator(); i.hasNext(); ) {
            builder.addSchemaFile(i.next());
        }
        builder.build(true);
        return builder;
    }

    private static QueryProfileRegistry createQueryProfileRegistryFromDirectory(String dir) {
        File queryProfilesDir = new File(dir, "query-profiles");
        if ( ! queryProfilesDir.exists()) return new QueryProfileRegistry();
        return new QueryProfileXMLReader().read(queryProfilesDir.toString());
    }

    // TODO: The build methods below just call the create methods above - remove

    /**
     * Convenience factory method to import and build a {@link Schema} object from a file. Only for testing.
     *
     * @param fileName the file to build from
     * @return the built {@link Schema} object
     * @throws IOException    thrown if there was a problem reading the file
     * @throws ParseException thrown if there was a problem parsing the file content
     */
    public static Schema buildFromFile(String fileName) throws IOException, ParseException {
        return buildFromFile(fileName, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfileRegistry());
    }

    /**
     * Convenience factory method to import and build a {@link Schema} object from a file.
     *
     * @param fileName the file to build from
     * @param rankProfileRegistry registry for rank profiles
     * @return the built {@link Schema} object
     * @throws IOException    thrown if there was a problem reading the file
     * @throws ParseException thrown if there was a problem parsing the file content
     */
    public static Schema buildFromFile(String fileName,
                                       RankProfileRegistry rankProfileRegistry,
                                       QueryProfileRegistry queryProfileRegistry)
            throws IOException, ParseException {
        return buildFromFile(fileName, new BaseDeployLogger(), rankProfileRegistry, queryProfileRegistry);
    }

    /**
     * Convenience factory method to import and build a {@link Schema} from a file.
     *
     * @param fileName the file to build from
     * @param deployLogger logger for deploy messages
     * @param rankProfileRegistry registry for rank profiles
     * @return the built {@link Schema} object
     * @throws IOException    thrown if there was a problem reading the file
     * @throws ParseException thrown if there was a problem parsing the file content
     */
    public static Schema buildFromFile(String fileName,
                                       DeployLogger deployLogger,
                                       RankProfileRegistry rankProfileRegistry,
                                       QueryProfileRegistry queryProfileRegistry)
            throws IOException, ParseException {
        return createFromFile(fileName, deployLogger, rankProfileRegistry, queryProfileRegistry).getSchema();
    }

    /**
     * Convenience factory method to import and build a {@link Schema} object from a raw object.
     *
     * @param rawSchema the raw object to build from
     * @return the built {@link ApplicationBuilder} object
     * @see #add(Schema)
     */
    public static ApplicationBuilder createFromRawSchema(Schema rawSchema,
                                                         RankProfileRegistry rankProfileRegistry,
                                                         QueryProfileRegistry queryProfileRegistry) {
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry, queryProfileRegistry);
        builder.add(rawSchema);
        builder.build(true);
        return builder;
    }

    /**
     * Convenience factory method to import and build a {@link Schema} object from a raw object.
     *
     * @param rawSchema the raw object to build from
     * @return the built {@link Schema} object
     * @see #add(Schema)
     */
    public static Schema buildFromRawSchema(Schema rawSchema,
                                            RankProfileRegistry rankProfileRegistry,
                                            QueryProfileRegistry queryProfileRegistry) {
        return createFromRawSchema(rawSchema, rankProfileRegistry, queryProfileRegistry).getSchema();
    }

    public RankProfileRegistry getRankProfileRegistry() {
        return rankProfileRegistry;
    }

    public QueryProfileRegistry getQueryProfileRegistry() {
        return queryProfileRegistry;
    }

    public ModelContext.Properties getProperties() { return properties; }

    public DeployLogger getDeployLogger() { return deployLogger; }

}

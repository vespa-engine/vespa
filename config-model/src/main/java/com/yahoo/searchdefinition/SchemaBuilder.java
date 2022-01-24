// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileXMLReader;
import com.yahoo.searchdefinition.derived.SearchOrderer;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.searchdefinition.parser.SDParser;
import com.yahoo.searchdefinition.parser.SimpleCharStream;
import com.yahoo.searchdefinition.parser.TokenMgrException;
import com.yahoo.searchdefinition.processing.Processing;
import com.yahoo.searchdefinition.processing.Processor;
import com.yahoo.vespa.documentmodel.DocumentModel;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Application builder. Usage:
 * 1) Add all schemas, using the addXXX() methods,
 * 2) provide the available rank types and rank expressions, using the setRankXXX() methods,
 * 3) invoke the {@link #build()} method
 */
public class SchemaBuilder {

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
    public SchemaBuilder() {
        this(new RankProfileRegistry(), new QueryProfileRegistry());
    }

    /** For testing only */
    public SchemaBuilder(DeployLogger deployLogger) {
        this(MockApplicationPackage.createEmpty(), deployLogger);
    }

    /** For testing only */
    public SchemaBuilder(DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry) {
        this(MockApplicationPackage.createEmpty(), deployLogger, rankProfileRegistry);
    }

    /** Used for generating documents for typed access to document fields in Java */
    public SchemaBuilder(boolean documentsOnly) {
        this(MockApplicationPackage.createEmpty(), new MockFileRegistry(), new BaseDeployLogger(), new TestProperties(), new RankProfileRegistry(), new QueryProfileRegistry(), documentsOnly);
    }

    /** For testing only */
    public SchemaBuilder(ApplicationPackage app, DeployLogger deployLogger) {
        this(app, new MockFileRegistry(), deployLogger, new TestProperties(), new RankProfileRegistry(), new QueryProfileRegistry());
    }

    /** For testing only */
    public SchemaBuilder(ApplicationPackage app, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry) {
        this(app, new MockFileRegistry(), deployLogger, new TestProperties(), rankProfileRegistry, new QueryProfileRegistry());
    }

    /** For testing only */
    public SchemaBuilder(RankProfileRegistry rankProfileRegistry) {
        this(rankProfileRegistry, new QueryProfileRegistry());
    }

    /** For testing only */
    public SchemaBuilder(RankProfileRegistry rankProfileRegistry, QueryProfileRegistry queryProfileRegistry) {
        this(rankProfileRegistry, queryProfileRegistry, new TestProperties());
    }

    public SchemaBuilder(RankProfileRegistry rankProfileRegistry, QueryProfileRegistry queryProfileRegistry, ModelContext.Properties properties) {
        this(MockApplicationPackage.createEmpty(), new MockFileRegistry(), new BaseDeployLogger(), properties, rankProfileRegistry, queryProfileRegistry);
    }

    public SchemaBuilder(ApplicationPackage app,
                         FileRegistry fileRegistry,
                         DeployLogger deployLogger,
                         ModelContext.Properties properties,
                         RankProfileRegistry rankProfileRegistry,
                         QueryProfileRegistry queryProfileRegistry) {
        this(app, fileRegistry, deployLogger, properties, rankProfileRegistry, queryProfileRegistry, false);
    }

    private SchemaBuilder(ApplicationPackage applicationPackage,
                          FileRegistry fileRegistry,
                          DeployLogger deployLogger,
                          ModelContext.Properties properties,
                          RankProfileRegistry rankProfileRegistry,
                          QueryProfileRegistry queryProfileRegistry,
                          boolean documentsOnly) {
        this.applicationPackage = applicationPackage;
        this.rankProfileRegistry = rankProfileRegistry;
        this.queryProfileRegistry = queryProfileRegistry;
        this.fileRegistry = fileRegistry;
        this.deployLogger = deployLogger;
        this.properties = properties;
        this.documentsOnly = documentsOnly;
    }

    /**
     * Import search definition.
     *
     * @param fileName the name of the file to import
     * @return the name of the imported object
     * @throws IOException    thrown if the file can not be read for some reason
     * @throws ParseException thrown if the file does not contain a valid search definition
     */
    public Schema addSchemaFile(String fileName) throws IOException, ParseException {
        File file = new File(fileName);
        return addSchema(IOUtils.readFile(file), file.getAbsoluteFile().getParent());
    }

    private Schema addSchemaFile(Path file) throws IOException, ParseException {
        return addSchemaFile(file.toString());
    }

    public void importFromApplicationPackage() {
        for (NamedReader reader : applicationPackage.getSchemas()) {
            importFrom(reader);
        }
    }

    /**
     * Reads and parses the schema string provided by the given reader. Once all schemas have been
     * imported, call {@link #build()}.
     *
     * @param reader  the reader whose content to import
     */
    private void importFrom(NamedReader reader) {
        try {
            String schemaName = addSchema(IOUtils.readAll(reader), reader.getName()).getName();
            String schemaFileName = stripSuffix(reader.getName(), ApplicationPackage.SD_NAME_SUFFIX);
            if ( ! schemaFileName.equals(schemaName)) {
                throw new IllegalArgumentException("The file containing schema '" + schemaName + "' must be named '" +
                                                   schemaName + ApplicationPackage.SD_NAME_SUFFIX + "', not " + reader.getName());
            }
        } catch (ParseException e) {
            throw new IllegalArgumentException("Could not parse schema file '" + reader.getName() + "'", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read schema file '" + reader.getName() + "'", e);
        } finally {
            closeIgnoreException(reader.getReader());
        }
    }

    private static String stripSuffix(String readerName, String suffix) {
        if ( ! readerName.endsWith(suffix))
            throw new IllegalArgumentException("Schema '" + readerName + "' does not end with " + suffix);
        return readerName.substring(0, readerName.length() - suffix.length());
    }

    /**
     * Adds a schema to this.
     *
     * @param string the string to parse
     * @return the schema
     * @throws ParseException thrown if the file does not contain a valid search definition
     */
    public Schema addSchema(String string) throws ParseException {
        return addSchema(string, null);
    }

    private Schema addSchema(String str, String schemaDir) throws ParseException {
        SimpleCharStream stream = new SimpleCharStream(str);
        try {
            Schema schema = new SDParser(stream, applicationPackage, fileRegistry, deployLogger, properties,
                                         rankProfileRegistry, documentsOnly)
                    .schema(documentTypeManager, schemaDir);
            addSchemaFile(schema);
            return schema;
        } catch (TokenMgrException e) {
            throw new ParseException("Unknown symbol: " + e.getMessage());
        } catch (ParseException pe) {
            throw new ParseException(stream.formatException(Exceptions.toMessageString(pe)));
        }
    }

    /**
     * Registers the given schema to the application to be built during {@link #build()}. A
     * {@link Schema} object is considered to be "raw" if it has not already been processed. This is the case for most
     * programmatically constructed schemas used in unit tests.
     *
     * @param schema the object to import
     * @throws IllegalArgumentException if the given search object has already been processed
     */
    public void addSchemaFile(Schema schema) {
        if (schema.getName() == null)
            throw new IllegalArgumentException("Schema has no name");
        schemas.add(schema);
    }

    /**
     * Processes and finalizes the schemas of this.
     * Only for testing.
     *
     * @throws IllegalStateException Thrown if this method has already been called.
     */
    public void build() {
        build(true);
    }

    /**
     * Processes and finalizes the schemas of this.
     *
     * @throws IllegalStateException thrown if this method has already been called
     */
    public void build(boolean validate) {
        if (application != null) throw new IllegalStateException("Application already built");

        application = new Application(applicationPackage,
                                      schemas,
                                      rankProfileRegistry,
                                      new QueryProfiles(queryProfileRegistry, deployLogger),
                                      properties,
                                      documentsOnly,
                                      validate,
                                      processorsToSkip,
                                      deployLogger);
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
     * @throws IllegalStateException if {@link #build()} has not been called.
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
     * @return the built {@link SchemaBuilder} object
     * @throws ParseException thrown if there is a problem parsing the string
     */
    public static SchemaBuilder createFromString(String sd) throws ParseException {
        return createFromString(sd, new BaseDeployLogger());
    }

    public static SchemaBuilder createFromString(String sd, DeployLogger logger) throws ParseException {
        SchemaBuilder builder = new SchemaBuilder(logger);
        builder.addSchema(sd);
        builder.build(true);
        return builder;
    }

    public static SchemaBuilder createFromStrings(DeployLogger logger, String ... schemas) throws ParseException {
        SchemaBuilder builder = new SchemaBuilder(logger);
        for (var schema : schemas)
            builder.addSchema(schema);
        builder.build(true);
        return builder;
    }

    /**
     * Convenience factory method to import and build a {@link Schema} object from a file. Only for testing.
     *
     * @param fileName the file to build from
     * @return the built {@link SchemaBuilder} object
     * @throws IOException    if there was a problem reading the file.
     * @throws ParseException if there was a problem parsing the file content.
     */
    public static SchemaBuilder createFromFile(String fileName) throws IOException, ParseException {
        return createFromFile(fileName, new BaseDeployLogger());
    }

    /**
     * Convenience factory methdd to create a SearchBuilder from multiple SD files. Only for testing.
     */
    public static SchemaBuilder createFromFiles(Collection<String> fileNames) throws IOException, ParseException {
        return createFromFiles(fileNames, new BaseDeployLogger());
    }

    public static SchemaBuilder createFromFile(String fileName, DeployLogger logger) throws IOException, ParseException {
        return createFromFile(fileName, logger, new RankProfileRegistry(), new QueryProfileRegistry());
    }

    private static SchemaBuilder createFromFiles(Collection<String> fileNames, DeployLogger logger) throws IOException, ParseException {
        return createFromFiles(fileNames, new MockFileRegistry(), logger, new TestProperties(), new RankProfileRegistry(), new QueryProfileRegistry());
    }

    /**
     * Convenience factory method to import and build a {@link Schema} object from a file.
     *
     * @param fileName the file to build from.
     * @param deployLogger logger for deploy messages.
     * @param rankProfileRegistry registry for rank profiles.
     * @return the built {@link SchemaBuilder} object.
     * @throws IOException    if there was a problem reading the file.
     * @throws ParseException if there was a problem parsing the file content.
     */
    private static SchemaBuilder createFromFile(String fileName,
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
    private static SchemaBuilder createFromFiles(Collection<String> fileNames,
                                                 FileRegistry fileRegistry,
                                                 DeployLogger deployLogger,
                                                 ModelContext.Properties properties,
                                                 RankProfileRegistry rankProfileRegistry,
                                                 QueryProfileRegistry queryprofileRegistry)
            throws IOException, ParseException {
        SchemaBuilder builder = new SchemaBuilder(MockApplicationPackage.createEmpty(),
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


    public static SchemaBuilder createFromDirectory(String dir, FileRegistry fileRegistry, DeployLogger logger, ModelContext.Properties properties) throws IOException, ParseException {
        return createFromDirectory(dir, fileRegistry, logger, properties, new RankProfileRegistry());
    }
    public static SchemaBuilder createFromDirectory(String dir,
                                                    FileRegistry fileRegistry,
                                                    DeployLogger logger,
                                                    ModelContext.Properties properties,
                                                    RankProfileRegistry rankProfileRegistry) throws IOException, ParseException {
        return createFromDirectory(dir, fileRegistry, logger, properties, rankProfileRegistry, createQueryProfileRegistryFromDirectory(dir));
    }
    private static SchemaBuilder createFromDirectory(String dir,
                                                     FileRegistry fileRegistry,
                                                     DeployLogger logger,
                                                     ModelContext.Properties properties,
                                                     RankProfileRegistry rankProfileRegistry,
                                                     QueryProfileRegistry queryProfileRegistry) throws IOException, ParseException {
        return createFromDirectory(dir, MockApplicationPackage.fromSearchDefinitionAndRootDirectory(dir), fileRegistry, logger, properties,
                                   rankProfileRegistry, queryProfileRegistry);
    }

    private static SchemaBuilder createFromDirectory(String dir,
                                                     ApplicationPackage applicationPackage,
                                                     FileRegistry fileRegistry,
                                                     DeployLogger deployLogger,
                                                     ModelContext.Properties properties,
                                                     RankProfileRegistry rankProfileRegistry,
                                                     QueryProfileRegistry queryProfileRegistry) throws IOException, ParseException {
        SchemaBuilder builder = new SchemaBuilder(applicationPackage,
                                                  fileRegistry,
                                                  deployLogger,
                                                  properties,
                                                  rankProfileRegistry,
                                                  queryProfileRegistry);
        for (Iterator<Path> i = Files.list(new File(dir).toPath()).filter(p -> p.getFileName().toString().endsWith(".sd")).iterator(); i.hasNext(); ) {
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
     * @return the built {@link SchemaBuilder} object
     * @see #addSchemaFile(Schema)
     */
    public static SchemaBuilder createFromRawSchema(Schema rawSchema,
                                                    RankProfileRegistry rankProfileRegistry,
                                                    QueryProfileRegistry queryProfileRegistry) {
        SchemaBuilder builder = new SchemaBuilder(rankProfileRegistry, queryProfileRegistry);
        builder.addSchemaFile(rawSchema);
        builder.build();
        return builder;
    }

    /**
     * Convenience factory method to import and build a {@link Schema} object from a raw object.
     *
     * @param rawSchema the raw object to build from
     * @return the built {@link Schema} object
     * @see #addSchemaFile(Schema)
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

    @SuppressWarnings("EmptyCatchBlock")
    private static void closeIgnoreException(Reader reader) {
        try {
            reader.close();
        } catch(Exception e) {}
    }
}

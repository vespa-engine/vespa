// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.derived.SearchOrderer;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.searchdefinition.parser.SDParser;
import com.yahoo.searchdefinition.parser.SimpleCharStream;
import com.yahoo.searchdefinition.parser.TokenMgrError;
import com.yahoo.searchdefinition.processing.Processing;
import com.yahoo.vespa.documentmodel.DocumentModel;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Helper class for importing {@link Search} objects in an unambiguous way. The pattern for using this is to 1) Import
 * all available search definitions, using the importXXX() methods, 2) provide the available rank types and rank
 * expressions, using the setRankXXX() methods, 3) invoke the {@link #build()} method, and 4) retrieve the built
 * search objects using the {@link #getSearch(String)} method.
 */
public class SearchBuilder {

    private final DocumentTypeManager docTypeMgr = new DocumentTypeManager();
    private List<Search> searchList = new LinkedList<>();
    private ApplicationPackage app = null;
    private boolean isBuilt = false;
    private DocumentModel model = new DocumentModel();
    private final RankProfileRegistry rankProfileRegistry;
    private final QueryProfileRegistry queryProfileRegistry;

    /** For testing only */
    public SearchBuilder() {
        this(MockApplicationPackage.createEmpty(), new RankProfileRegistry(), new QueryProfileRegistry());
    }

    /** For testing only */
    public SearchBuilder(ApplicationPackage app) {
        this(app, new RankProfileRegistry(), new QueryProfileRegistry());
    }

    /** For testing only */
    public SearchBuilder(RankProfileRegistry rankProfileRegistry) {
        this(MockApplicationPackage.createEmpty(), rankProfileRegistry, new QueryProfileRegistry());
    }

    /** For testing only */
    public SearchBuilder(RankProfileRegistry rankProfileRegistry, QueryProfileRegistry queryProfileRegistry) {
        this(MockApplicationPackage.createEmpty(), rankProfileRegistry, queryProfileRegistry);
    }

    public SearchBuilder(ApplicationPackage app,
                         RankProfileRegistry rankProfileRegistry,
                         QueryProfileRegistry queryProfileRegistry) {
        this.app = app;
        this.rankProfileRegistry = rankProfileRegistry;
        this.queryProfileRegistry = queryProfileRegistry;
    }

    /**
     * Import search definition.
     *
     * @param fileName The name of the file to import.
     * @param deployLogger Logger for deploy messages.
     * @return The name of the imported object.
     * @throws IOException    Thrown if the file can not be read for some reason.
     * @throws ParseException Thrown if the file does not contain a valid search definition.       ```
     */
    public String importFile(String fileName, DeployLogger deployLogger) throws IOException, ParseException {
        File file = new File(fileName);
        return importString(IOUtils.readFile(file), file.getAbsoluteFile().getParent(), deployLogger);
    }

    /**
     * Import search definition.
     *
     * @param fileName The name of the file to import.
     * @return The name of the imported object.
     * @throws IOException    Thrown if the file can not be read for some reason.
     * @throws ParseException Thrown if the file does not contain a valid search definition.
     */
    public String importFile(String fileName) throws IOException, ParseException {
        return importFile(fileName, new BaseDeployLogger());
    }
    public String importFile(Path file) throws IOException, ParseException {
        return importFile(file.toString(), new BaseDeployLogger());
    }

    /**
     * Reads and parses the search definition string provided by the given reader. Once all search definitions have been
     * imported, call {@link #build()}.
     *
     * @param reader       The reader whose content to import.
     * @param searchDefDir The path to use when resolving file references.
     * @return The name of the imported object.
     * @throws ParseException Thrown if the file does not contain a valid search definition.
     */
    public String importReader(NamedReader reader, String searchDefDir, DeployLogger deployLogger) throws IOException, ParseException {
        return importString(IOUtils.readAll(reader), searchDefDir, deployLogger);
    }

    /**
     * See #{@link #importReader}
     *
     * Convenience, should only be used for testing as logs will be swallowed.
     */
    public String importReader(NamedReader reader, String searchDefDir) throws IOException, ParseException {
        return importString(IOUtils.readAll(reader), searchDefDir, new BaseDeployLogger());
    }

    /**
     * Import search definition.
     *
     * @param str the string to parse.
     * @return the name of the imported object.
     * @throws ParseException thrown if the file does not contain a valid search definition.
     */
    public String importString(String str) throws ParseException {
        return importString(str, null, new BaseDeployLogger());
    }

    /**
     * Import search definition.
     *
     * @param str the string to parse.
     * @return the name of the imported object.
     * @throws ParseException thrown if the file does not contain a valid search definition.
     */
    public String importString(String str, DeployLogger logger) throws ParseException {
        return importString(str, null, logger);
    }

    private String importString(String str, String searchDefDir, DeployLogger deployLogger) throws ParseException {
        Search search;
        SimpleCharStream stream = new SimpleCharStream(str);
        try {
            search = new SDParser(stream, deployLogger, app, rankProfileRegistry).search(docTypeMgr, searchDefDir);
        } catch (TokenMgrError e) {
            throw new ParseException("Unknown symbol: " + e.getMessage());
        } catch (ParseException pe) {
            throw new ParseException(stream.formatException(pe.getMessage()));
        }
        return importRawSearch(search);
    }

    /**
     * Registers the given search object to the internal list of objects to be processed during {@link #build()}. A
     * {@link Search} object is considered to be "raw" if it has not already been processed. This is the case for most
     * programmatically constructed search objects used in unit tests.
     *
     * @param rawSearch The object to import.
     * @return The name of the imported object.
     * @throws IllegalArgumentException Thrown if the given search object has already been processed.
     */
    public String importRawSearch(Search rawSearch) {
        if (rawSearch.getName() == null) {
            throw new IllegalArgumentException("Search has no name.");
        }
        String rawName = rawSearch.getName();
        if (rawSearch.isProcessed()) {
            throw new IllegalArgumentException("A search definition with a search section called '" + rawName +
                                               "' has already been processed.");
        }
        for (Search search : searchList) {
            if (rawName.equals(search.getName())) {
                throw new IllegalArgumentException("A search definition with a search section called '" + rawName +
                                                   "' has already been added.");
            }
        }
        searchList.add(rawSearch);
        return rawName;
    }

    /**
     * Only for testing.
     *
     * Processes and finalizes the imported search definitions so that they become available through the {@link
     * #getSearch(String)} method.
     *
     * @throws IllegalStateException Thrown if this method has already been called.
     */
    public void build() {
        build(new BaseDeployLogger());
    }

    /**
     * Processes and finalizes the imported search definitions so that they become available through the {@link
     * #getSearch(String)} method.
     *
     * @throws IllegalStateException Thrown if this method has already been called.
     * @param deployLogger The logger to use during build
     */
    public void build(DeployLogger deployLogger) {
        if (isBuilt) throw new IllegalStateException("Model already built");

        List<Search> built = new ArrayList<>();
        List<SDDocumentType> sdocs = new ArrayList<>();
        sdocs.add(SDDocumentType.VESPA_DOCUMENT);
        for (Search search : searchList) {
            if (search.hasDocument()) {
                sdocs.add(search.getDocument());
            }
        }
        SDDocumentTypeOrderer orderer = new SDDocumentTypeOrderer(sdocs, deployLogger);
        orderer.process();
        for (SDDocumentType sdoc : orderer.getOrdered()) {
            new FieldOperationApplierForStructs().process(sdoc);
            new FieldOperationApplier().process(sdoc);
        }

        DocumentReferenceResolver resolver = new DocumentReferenceResolver(searchList);
        sdocs.forEach(resolver::resolveReferences);

        DocumentGraphValidator validator = new DocumentGraphValidator();
        validator.validateDocumentGraph(sdocs);

        DocumentModelBuilder builder = new DocumentModelBuilder(model);
        for (Search search : new SearchOrderer().order(searchList)) {
            new FieldOperationApplierForSearch().process(search);
            // These two needed for a couple of old unit tests, ideally these are just read from app
            process(search, deployLogger, new QueryProfiles(queryProfileRegistry));
            built.add(search);
        }
        builder.addToModel(searchList);
        if ( ! builder.valid() ) {
            throw new IllegalArgumentException("Impossible to build a correct model.");
        }
        searchList = built;
        isBuilt = true;
    }

    /**
     * Processes and returns the given {@link Search} object. This method has been factored out of the {@link
     * #build()} method so that subclasses can choose not to build anything.
     */
    protected void process(Search search, DeployLogger deployLogger, QueryProfiles queryProfiles) {
        Processing.process(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    /**
     * Convenience method to call {@link #getSearch(String)} when there is only a single {@link Search} object
     * built. This method will never return null.
     *
     * @return The build object.
     * @throws IllegalStateException Thrown if there is not exactly one search.
     */
    public Search getSearch() {
        if ( ! isBuilt)  throw new IllegalStateException("Searches not built.");
        if (searchList.size() != 1)
            throw new IllegalStateException("This call only works if we have 1 search definition. Search definitions: " + searchList);

        return searchList.get(0);
    }

    public DocumentModel getModel() {
        return model;
    }

    /**
     * Returns the built {@link Search} object that has the given name. If the name is unknown, this method will simply
     * return null.
     *
     * @param name the name of the search definition to return,
     *             or null to return the only one or throw an exception if there are multiple to choose from
     * @return the built object, or null if none with this name
     * @throws IllegalStateException if {@link #build()} has not been called.
     */
    public Search getSearch(String name) {
        if ( ! isBuilt)  throw new IllegalStateException("Searches not built.");
        if (name == null) return getSearch();

        for (Search search : searchList)
            if (search.getName().equals(name)) return search;
        return null;
    }

    /**
     * Convenience method to return a list of all built {@link Search} objects.
     *
     * @return The list of built searches.
     */
    public List<Search> getSearchList() {
        return new ArrayList<>(searchList);
    }

    /**
     * Convenience factory method to import and build a {@link Search} object from a string.
     *
     * @param sd   The string to build from.
     * @return The built {@link SearchBuilder} object.
     * @throws ParseException Thrown if there was a problem parsing the string.
     */
    public static SearchBuilder createFromString(String sd) throws ParseException {
        SearchBuilder builder = new SearchBuilder(MockApplicationPackage.createEmpty());
        builder.importString(sd);
        builder.build();
        return builder;
    }

    /**
     * Convenience factory method to import and build a {@link Search} object from a file. Only for testing.
     *
     * @param fileName the file to build from
     * @return the built {@link SearchBuilder} object
     * @throws IOException    if there was a problem reading the file.
     * @throws ParseException if there was a problem parsing the file content.
     */
    public static SearchBuilder createFromFile(String fileName) throws IOException, ParseException {
        return createFromFile(fileName, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfileRegistry());
    }

    /**
     * Convenience factory method to import and build a {@link Search} object from a file.
     *
     * @param fileName the file to build from.
     * @param deployLogger logger for deploy messages.
     * @param rankProfileRegistry registry for rank profiles.
     * @return the built {@link SearchBuilder} object.
     * @throws IOException    if there was a problem reading the file.
     * @throws ParseException if there was a problem parsing the file content.
     */
    public static SearchBuilder createFromFile(String fileName,
                                               DeployLogger deployLogger,
                                               RankProfileRegistry rankProfileRegistry,
                                               QueryProfileRegistry queryprofileRegistry)
            throws IOException, ParseException {
        SearchBuilder builder = new SearchBuilder(MockApplicationPackage.createEmpty(),
                                                  rankProfileRegistry,
                                                  queryprofileRegistry);
        builder.importFile(fileName);
        builder.build(deployLogger);
        return builder;
    }

    public static SearchBuilder createFromDirectory(String dir) throws IOException, ParseException {
        return createFromDirectory(dir, new RankProfileRegistry(), new QueryProfileRegistry());
    }
    public static SearchBuilder createFromDirectory(String dir,
                                                    RankProfileRegistry rankProfileRegistry,
                                                    QueryProfileRegistry queryProfileRegistry) throws IOException, ParseException {
        SearchBuilder builder = new SearchBuilder(MockApplicationPackage.fromSearchDefinitionDirectory(dir),
                                                  rankProfileRegistry,
                                                  queryProfileRegistry);
        for (Iterator<Path> i = Files.list(new File(dir).toPath()).filter(p -> p.getFileName().toString().endsWith(".sd")).iterator(); i.hasNext(); ) {
            builder.importFile(i.next());
        }
        builder.build(new BaseDeployLogger());
        return builder;
    }

    // TODO: The build methods below just call the create methods above - remove

    /**
     * Convenience factory method to import and build a {@link Search} object from a file. Only for testing.
     *
     * @param fileName The file to build from.
     * @return The built {@link Search} object.
     * @throws IOException    Thrown if there was a problem reading the file.
     * @throws ParseException Thrown if there was a problem parsing the file content.
     */
    public static Search buildFromFile(String fileName) throws IOException, ParseException {
        return buildFromFile(fileName, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfileRegistry());
    }

    /**
     * Convenience factory method to import and build a {@link Search} object from a file.
     *
     * @param fileName The file to build from.
     * @param rankProfileRegistry Registry for rank profiles.
     * @return The built {@link Search} object.
     * @throws IOException    Thrown if there was a problem reading the file.
     * @throws ParseException Thrown if there was a problem parsing the file content.
     */
    public static Search buildFromFile(String fileName,
                                       RankProfileRegistry rankProfileRegistry,
                                       QueryProfileRegistry queryProfileRegistry)
            throws IOException, ParseException {
        return buildFromFile(fileName, new BaseDeployLogger(), rankProfileRegistry, queryProfileRegistry);
    }

    /**
     * Convenience factory method to import and build a {@link Search} object from a file.
     *
     * @param fileName The file to build from.
     * @param deployLogger Logger for deploy messages.
     * @param rankProfileRegistry Registry for rank profiles.
     * @return The built {@link Search} object.
     * @throws IOException    Thrown if there was a problem reading the file.
     * @throws ParseException Thrown if there was a problem parsing the file content.
     */
    public static Search buildFromFile(String fileName,
                                       DeployLogger deployLogger,
                                       RankProfileRegistry rankProfileRegistry,
                                       QueryProfileRegistry queryProfileRegistry)
            throws IOException, ParseException {
        return createFromFile(fileName, deployLogger, rankProfileRegistry, queryProfileRegistry).getSearch();
    }

    /**
     * Convenience factory method to import and build a {@link Search} object from a raw object.
     *
     * @param rawSearch the raw object to build from.
     * @return the built {@link SearchBuilder} object.
     * @see #importRawSearch(Search)
     */
    public static SearchBuilder createFromRawSearch(Search rawSearch,
                                                    RankProfileRegistry rankProfileRegistry,
                                                    QueryProfileRegistry queryProfileRegistry) {
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry, queryProfileRegistry);
        builder.importRawSearch(rawSearch);
        builder.build();
        return builder;
    }

    /**
     * Convenience factory method to import and build a {@link Search} object from a raw object.
     *
     * @param rawSearch The raw object to build from.
     * @return The built {@link Search} object.
     * @see #importRawSearch(Search)
     */
    public static Search buildFromRawSearch(Search rawSearch,
                                            RankProfileRegistry rankProfileRegistry,
                                            QueryProfileRegistry queryProfileRegistry) {
        return createFromRawSearch(rawSearch, rankProfileRegistry, queryProfileRegistry).getSearch();
    }

    public RankProfileRegistry getRankProfileRegistry() {
        return rankProfileRegistry;
    }

    public QueryProfileRegistry getQueryProfileRegistry() {
        return queryProfileRegistry;
    }

}

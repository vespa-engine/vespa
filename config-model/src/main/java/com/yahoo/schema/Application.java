// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.schema.derived.SearchOrderer;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.processing.Processing;
import com.yahoo.schema.processing.Processor;
import com.yahoo.vespa.documentmodel.DocumentModel;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A collection of objects representing the content of an application package.
 * This is created, then added to, and lastly validated when there is no more content to add.
 * At that point it is ready to use for deriving configuration.
 *
 * @author bratseth
 */
public class Application {

    private final ApplicationPackage applicationPackage;
    private final Map<String, Schema> schemas;
    private final DocumentModel documentModel;

    public Application(ApplicationPackage applicationPackage,
                       List<Schema> schemas,
                       RankProfileRegistry rankProfileRegistry,
                       QueryProfiles queryProfiles,
                       ModelContext.Properties properties,
                       boolean documentsOnly,
                       boolean validate,
                       Set<Class<? extends Processor>> processorsToSkip,
                       DeployLogger logger) {
        this.applicationPackage = applicationPackage;

        Map<String, Schema> schemaMap = new LinkedHashMap<>();
        for (Schema schema : schemas) {
            if (schemaMap.containsKey(schema.getName()))
                throw new IllegalArgumentException("Duplicate schema '" + schema.getName() + "' in " + this);
            schemaMap.put(schema.getName(), schema);
        }
        this.schemas = Collections.unmodifiableMap(schemaMap);

        schemas.forEach(schema -> schema.setOwner(this));
        if (validate)
            schemas.forEach(schema -> schema.validate(logger));

        List<SDDocumentType> sdocs = new ArrayList<>();
        sdocs.add(SDDocumentType.VESPA_DOCUMENT);
        for (Schema schema : schemas) {
            if (schema.hasDocument()) {
                sdocs.add(schema.getDocument());
            }
        }

        var resolver = new DocumentReferenceResolver(schemas);
        sdocs.forEach(resolver::resolveReferences);
        sdocs.forEach(resolver::resolveInheritedReferences);
        var importedFieldsEnumerator = new ImportedFieldsEnumerator(schemas);
        sdocs.forEach(importedFieldsEnumerator::enumerateImportedFields);

        if (validate)
            new DocumentGraphValidator().validateDocumentGraph(sdocs);

        List<Schema> schemasSomewhatOrdered = new ArrayList<>(schemas);
        for (Schema schema : new SearchOrderer().order(schemasSomewhatOrdered)) {
            new Processing(properties).process(schema,
                                               logger,
                                               rankProfileRegistry,
                                               queryProfiles,
                                               validate,
                                               documentsOnly,
                                               processorsToSkip);
        }

        this.documentModel = new DocumentModelBuilder().build(schemasSomewhatOrdered);
    }

    public ApplicationPackage applicationPackage() { return applicationPackage; }

    /** Returns an unmodifiable list of the schemas of this application */
    public Map<String, Schema> schemas() { return schemas; }

    public DocumentModel documentModel() { return documentModel; }

    @Override
    public String toString() { return "application " + applicationPackage.getApplicationId(); }

}

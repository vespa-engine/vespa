// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.vespa.documentmodel.DocumentModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final DocumentModel documentModel = new DocumentModel();

    public Application(ApplicationPackage applicationPackage, List<Schema> schemas, DeployLogger logger) {
        this.applicationPackage = applicationPackage;

        Map<String, Schema> schemaMap = new LinkedHashMap<>();
        for (Schema schema : schemas) {
            if (schemaMap.containsKey(schema.getName()))
                throw new IllegalArgumentException("Duplicate schema '" + schema.getName() + "' in " + this);
            schemaMap.put(schema.getName(), schema);
        }
        this.schemas = Collections.unmodifiableMap(schemaMap);

        schemas.forEach(schema -> schema.setOwner(this));
        schemas.forEach(schema -> schema.validate(logger));
    }

    public ApplicationPackage applicationPackage() { return applicationPackage; }

    /** Returns an unmodifiable list of the schemas of this application */
    public Map<String, Schema> schemas() { return schemas; }

    public void buildDocumentModel(List<Schema> schemasSomewhatOrdered) {
        var builder = new DocumentModelBuilder(documentModel);
        builder.addToModel(schemasSomewhatOrdered);
    }

    public DocumentModel documentModel() { return documentModel; }

    @Override
    public String toString() { return "application " + applicationPackage.getApplicationId(); }

}

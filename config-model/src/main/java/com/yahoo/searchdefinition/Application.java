// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;

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
    private final RankProfileRegistry rankProfileRegistry;
    private final Map<String, Schema> schemas = new LinkedHashMap<>();

    public Application(ApplicationPackage applicationPackage) {
        this(applicationPackage, new RankProfileRegistry());
    }

    // TODO: Almost sure the rank profile registry passed is always new RankProfileRegistry() (apart from in some tests), so remove the parameter
    public Application(ApplicationPackage applicationPackage, RankProfileRegistry rankProfileRegistry) {
        this.applicationPackage = applicationPackage;
        this.rankProfileRegistry = rankProfileRegistry;
    }

    public ApplicationPackage applicationPackage() { return applicationPackage; }

    public RankProfileRegistry rankProfileRegistry() { return rankProfileRegistry; }

    public void add(Schema schema) {
        if (schemas.containsKey(schema.getName()))
            throw new IllegalArgumentException("Duplicate schema '" + schema.getName() + "' in " + this);
        schemas.put(schema.getName(), schema);
    }

    /** Returns an unmodifiable list of the schemas of this application */
    public Map<String, Schema> schemas() { return Collections.unmodifiableMap(schemas); }

    /** Used by SearchBuilder, for now */
    void replaceSchemasBy(List<Schema> schemas) {
        this.schemas.clear();
        for (var schema : schemas)
            this.schemas.put(schema.getName(), schema);
    }

    /** Validates this. Must be called after all content is added to it. */
    public void validate(DeployLogger logger) {
        schemas.values().forEach(schema -> schema.validate(logger));
    }

    @Override
    public String toString() { return "application " + applicationPackage.getApplicationId(); }

}

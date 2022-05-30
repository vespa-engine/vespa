// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing.multifieldresolver;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.Schema;
import java.util.List;

/**
 * Abstract superclass of all multifield conflict resolvers
 */
public abstract class MultiFieldResolver {

    protected String indexName;
    protected List<SDField> fields;
    protected Schema schema;

    protected DeployLogger deployLogger;

    public MultiFieldResolver(String indexName, List<SDField> fields, Schema schema, DeployLogger logger) {
        this.indexName = indexName;
        this.fields = fields;
        this.schema = schema;
        this.deployLogger = logger;
    }

    /**
     * Checks the list of fields for specific conflicts, and reports and/or
     * attempts to correct them
     */
    public abstract void resolve();

}

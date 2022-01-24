// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.deploy;

import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.SchemaBuilder;
import com.yahoo.vespa.documentmodel.DocumentModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal helper class to retrieve document model and schemas.
 *
 * @author Ulf Lilleengen
 */
// TODO: This should be removed in favor of Application
public class SearchDocumentModel {

    private final DocumentModel documentModel;
    private final List<Schema> schemas;

    public SearchDocumentModel(DocumentModel documentModel, List<Schema> schemas) {
        this.documentModel = documentModel;
        this.schemas = schemas;
    }

    public DocumentModel getDocumentModel() {
        return documentModel;
    }

    public List<Schema> getSchemas() {
        return schemas;
    }

    public static SearchDocumentModel fromBuilder(SchemaBuilder builder) {
        List<Schema> ret = new ArrayList<>();
        for (Schema schema : builder.getSchemaList()) {
            ret.add(schema);
        }
        return new SearchDocumentModel(builder.getModel(), ret);
    }

}

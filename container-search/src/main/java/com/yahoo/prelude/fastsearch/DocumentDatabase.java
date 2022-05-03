// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Representation of a document database realizing a schema in a content cluster.
 *
 * @author geirst
 */
public class DocumentDatabase {

    public static final String MATCH_PROPERTY = "match";
    public static final String SEARCH_DOC_TYPE_KEY = "documentdb.searchdoctype";

    private final Schema schema;
    private final DocsumDefinitionSet docsumDefSet;

    public DocumentDatabase(Schema schema) {
        this.schema = schema;
        this.docsumDefSet = new DocsumDefinitionSet(schema);
    }

    public Schema schema() { return schema; }

    /** Returns the document summary model in this which knows how to convert serialized data to hit fields. */
    public DocsumDefinitionSet getDocsumDefinitionSet() { return docsumDefSet; }

}

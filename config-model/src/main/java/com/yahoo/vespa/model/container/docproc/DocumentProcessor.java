// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.docproc;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;
import com.yahoo.vespa.model.container.docproc.model.DocumentProcessorModel;

import java.util.Map;

/**
 * @author Einar M R Rosenvinge
 */
public class DocumentProcessor extends ChainedComponent<DocumentProcessorModel> {

    public static final String INDEXER = "com.yahoo.docprocs.indexing.IndexingProcessor";

    private final Map<Pair<String, String>, String> fieldNameSchemaMap;

    public DocumentProcessor(DocumentProcessorModel model) {
        super(model);
        this.fieldNameSchemaMap = model.fieldNameSchemaMap();
    }

    /**
     * The field name schema map that applies to this docproc
     * @return doctype,from → to
     */
    public Map<Pair<String,String>,String> fieldNameSchemaMap() {
        return fieldNameSchemaMap;
    }

}

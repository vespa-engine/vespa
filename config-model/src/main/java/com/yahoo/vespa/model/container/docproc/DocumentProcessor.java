// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.docproc;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;
import com.yahoo.vespa.model.container.docproc.model.DocumentProcessorModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
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

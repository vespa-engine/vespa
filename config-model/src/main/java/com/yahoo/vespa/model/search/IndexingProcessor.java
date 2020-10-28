// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.vespa.model.container.docproc.DocumentProcessor;
import com.yahoo.vespa.model.container.docproc.model.DocumentProcessorModel;

import java.util.HashMap;
import java.util.List;

/**
 * @author Einar M R Rosenvinge
 */
public class IndexingProcessor extends DocumentProcessor {

    public static final String docprocsBundleSpecification = "docprocs";

    public IndexingProcessor() {
        super(new DocumentProcessorModel(new BundleInstantiationSpecification(new ComponentId(DocumentProcessor.INDEXER),
                                                                              new ComponentSpecification(DocumentProcessor.INDEXER),
                                                                              new ComponentSpecification(docprocsBundleSpecification)),
                                         new Dependencies(List.of(), List.of(), List.of()),
                                         new HashMap<>()));
    }

}

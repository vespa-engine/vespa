// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.docproc.model;

import com.yahoo.collections.Pair;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import net.jcip.annotations.Immutable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Einar M R Rosenvinge
 */
@Immutable
public class DocumentProcessorModel extends ChainedComponentModel {

    private final Map<Pair<String, String>, String> fieldNameSchemaMap = new HashMap<>();

    public DocumentProcessorModel(BundleInstantiationSpecification bundleInstantiationSpec, Dependencies dependencies, Map<Pair<String, String>, String> fieldNameSchemaMap) {
        super(bundleInstantiationSpec, dependencies);
        this.fieldNameSchemaMap.putAll(fieldNameSchemaMap);
    }

    /**
     * The field name schema map that applies to this docproc
     * @return doctype,from → to
     */
    public Map<Pair<String,String>,String> fieldNameSchemaMap() {
        return fieldNameSchemaMap;
    }

}

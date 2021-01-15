// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.docproc;

import com.yahoo.collections.Pair;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.vespa.model.container.component.chain.Chain;

import java.util.Map;

import static com.yahoo.container.core.ChainsConfig.Chains.Type;

/**
 * @author Einar M R Rosenvinge
 */
public class DocprocChain extends Chain<DocumentProcessor> {

    private final Map<Pair<String, String>, String> fieldNameSchemaMap;
    private static final Type.Enum TYPE = Type.Enum.DOCPROC;

    public DocprocChain(ChainSpecification specWithoutInnerComponents, Map<Pair<String,String>, String> fieldNameSchemaMap) {
        super(specWithoutInnerComponents);
        this.fieldNameSchemaMap = fieldNameSchemaMap;
    }

    /**
     * The field name schema map that applies to this whole chain
     * @return doctype,from → to
     */
    public Map<Pair<String,String>,String> fieldNameSchemaMap() {
        return fieldNameSchemaMap;
    }

    public String getServiceName() {
        return getParent().getParent().getParent().getConfigId() + "/" + getSessionName();
    }

    public String getSessionName() {
        return "chain." + getComponentId().stringValue();
    }

    public Type.Enum getType() {
        return TYPE;
    }

}

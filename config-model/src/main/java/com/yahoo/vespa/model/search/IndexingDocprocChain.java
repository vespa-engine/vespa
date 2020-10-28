// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Phase;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig;
import com.yahoo.vespa.model.container.docproc.DocprocChain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * @author Einar M R Rosenvinge
 */
public class IndexingDocprocChain extends DocprocChain implements SpecialtokensConfig.Producer {

    public static final String NAME = "indexing";
    private static final List<Phase> phases = new ArrayList<>(2);

    static {
        phases.add(new Phase("indexingStart", Set.of(), Set.of()));
        phases.add(new Phase("indexingEnd", Set.of(), Set.of()));
    }

    public IndexingDocprocChain() {
        super(new ChainSpecification(new ComponentId(NAME),
                                     new ChainSpecification.Inheritance(Set.of(), Set.of()),
                                     phases,
                                     Set.of()),
              new HashMap<>());
        addInnerComponent(new IndexingProcessor());
    }

    @Override
    public void getConfig(SpecialtokensConfig.Builder builder) {
    }

}

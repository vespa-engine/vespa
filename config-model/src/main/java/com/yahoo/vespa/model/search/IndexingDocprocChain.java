// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.collections.Pair;
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

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class IndexingDocprocChain extends DocprocChain implements SpecialtokensConfig.Producer {

    public static final String NAME = "indexing";
    private static final List<Phase> phases = new ArrayList<>(2);

    static {
        phases.add(new Phase("indexingStart", Collections.<String>emptySet(), Collections.<String>emptySet()));
        phases.add(new Phase("indexingEnd", Collections.<String>emptySet(), Collections.<String>emptySet()));
    }

    public IndexingDocprocChain() {
        super(new ChainSpecification(new ComponentId(NAME),
                                     new ChainSpecification.Inheritance(Collections.<ComponentSpecification>emptySet(),
                                                                        Collections.<ComponentSpecification>emptySet()),
                                     phases,
                                     Collections.<ComponentSpecification>emptySet()),
              new HashMap<>());
        addInnerComponent(new IndexingProcessor());
    }

    @Override
    public void getConfig(SpecialtokensConfig.Builder builder) {
    }

}

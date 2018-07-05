// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.chains.ChainsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainBuilderBase;
import com.yahoo.vespa.model.container.search.searchchain.SearchChain;
import com.yahoo.vespa.model.container.search.searchchain.Searcher;
import org.w3c.dom.Element;

import java.util.*;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * Creates top level search chains(searchchain, provider) from xml.
 */
public class SearchChainsBuilder extends ChainsBuilder<Searcher<?>, SearchChain> {

    private static final Map<String, Class<? extends DomChainBuilderBase<? extends Searcher<?>, ? extends SearchChain>>>
            chainType2builderClass = Collections.unmodifiableMap(
            new LinkedHashMap<String, Class<? extends DomChainBuilderBase<? extends Searcher<?>, ? extends SearchChain>>>() {{
                put("chain", DomSearchChainBuilder.class);
                put("searchchain", DomSearchChainBuilder.class);
                put("provider", DomProviderBuilder.class);
            }});

    public SearchChainsBuilder(AbstractConfigProducer ancestor, List<Element> searchChainsElements,
                               Map<String, ComponentsBuilder.ComponentType> outerSearcherTypeByComponentName) {
        super(ancestor, searchChainsElements, outerSearcherTypeByComponentName, chainType2builderClass);
    }

}

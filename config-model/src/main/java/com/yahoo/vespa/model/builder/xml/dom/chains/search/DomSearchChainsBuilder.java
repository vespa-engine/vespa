// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder.ComponentType;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainsBuilder;
import com.yahoo.vespa.model.container.search.searchchain.SearchChain;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.vespa.model.container.search.searchchain.Searcher;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Builds the search chains model from xml.
 *
 * @author Tony Vaagenes
 */
public class DomSearchChainsBuilder extends DomChainsBuilder<Searcher<?>, SearchChain, SearchChains> {

    public DomSearchChainsBuilder(Element outerChainsElem, boolean supportSearchChainsDir) {
        super(outerChainsElem, Arrays.asList(ComponentType.searcher, ComponentType.federation),
              supportSearchChainsDir ? ApplicationPackage.SEARCHCHAINS_DIR: null);
    }

    // For unit testing without outer chains
    public DomSearchChainsBuilder() {
        this(null, false);
    }

    @Override
    protected SearchChains newChainsInstance(AbstractConfigProducer parent) {
        return new SearchChains(parent, "searchchains");
    }

    @Override
    protected SearchChainsBuilder readChains(AbstractConfigProducer ancestor, List<Element> searchChainsElements,
                                             Map<String, ComponentType> outerComponentTypeByComponentName) {
        return new SearchChainsBuilder(ancestor, searchChainsElements, outerComponentTypeByComponentName);
    }

}

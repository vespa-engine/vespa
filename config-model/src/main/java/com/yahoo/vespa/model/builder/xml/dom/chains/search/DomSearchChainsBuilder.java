// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
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

    public DomSearchChainsBuilder() {
        super(Arrays.asList(ComponentType.searcher, ComponentType.federation));
    }

    @Override
    protected SearchChains newChainsInstance(TreeConfigProducer<AnyConfigProducer> parent) {
        return new SearchChains(parent, "searchchains");
    }

    @Override
    protected SearchChainsBuilder readChains(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, List<Element> searchChainsElements,
                                             Map<String, ComponentType<?>> outerComponentTypeByComponentName) {
        return new SearchChainsBuilder(deployState, ancestor, searchChainsElements, outerComponentTypeByComponentName);
    }

}

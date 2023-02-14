// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.chains.ChainsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainBuilderBase;
import com.yahoo.vespa.model.container.search.searchchain.SearchChain;
import com.yahoo.vespa.model.container.search.searchchain.Searcher;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates top level search chains(searchchain, provider) from xml.
 *
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class SearchChainsBuilder extends ChainsBuilder<Searcher<?>, SearchChain> {

    private static final Map<String, Class<? extends DomChainBuilderBase<? extends Searcher<?>, ? extends SearchChain>>>
            chainType2builderClass = Collections.unmodifiableMap(
            new LinkedHashMap<>() {{
                put("chain", DomSearchChainBuilder.class);
                put("provider", DomProviderBuilder.class);
            }});

    public SearchChainsBuilder(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, List<Element> searchChainsElements,
                               Map<String, ComponentsBuilder.ComponentType<?>> outerSearcherTypeByComponentName) {
        super(deployState, ancestor, searchChainsElements, outerSearcherTypeByComponentName, chainType2builderClass);
    }

}

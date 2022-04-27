// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainBuilderBase;
import com.yahoo.vespa.model.container.search.searchchain.SearchChain;
import com.yahoo.vespa.model.container.search.searchchain.Searcher;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Map;

/**
 * Builds a Search chain from xml.
 * @author Tony Vaagenes
 */
public class DomSearchChainBuilder extends DomChainBuilderBase<Searcher<?>, SearchChain> {

    public DomSearchChainBuilder(Map<String, ComponentsBuilder.ComponentType<?>> outerSearcherTypeByComponentName) {
        super(Arrays.asList(ComponentsBuilder.ComponentType.searcher, ComponentsBuilder.ComponentType.federation),
                outerSearcherTypeByComponentName);
    }

    protected SearchChain buildChain(DeployState deployState, AbstractConfigProducer<?> ancestor, Element producerSpec,
                                     ChainSpecification specWithoutInnerComponents) {
        return new SearchChain(specWithoutInnerComponents);
    }

}

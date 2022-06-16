// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.chains.ChainsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder.ComponentType;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainBuilderBase;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainsBuilder;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.http.Filter;
import com.yahoo.vespa.model.container.http.FilterChains;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Tony Vaagenes
 */
public class FilterChainsBuilder extends DomChainsBuilder<Filter, Chain<Filter>, FilterChains>  {
    private static final Collection<ComponentType<Filter>> allowedComponentTypes =
            Collections.singleton(ComponentType.filter);

    private static final Map<String, Class<? extends DomChainBuilderBase<? extends Filter, ? extends Chain<Filter>>>> chainType2BuilderClass =
            Map.of(
                    HttpBuilder.REQUEST_CHAIN_TAG_NAME, FilterChainBuilder.class,
                    HttpBuilder.RESPONSE_CHAIN_TAG_NAME, FilterChainBuilder.class);

    public FilterChainsBuilder() {
        super(allowedComponentTypes);
    }

    @Override
    protected FilterChains newChainsInstance(AbstractConfigProducer<?> parent) {
        return new FilterChains(parent);
    }

    @Override
    protected ChainsBuilder<Filter, Chain<Filter>> readChains(
            DeployState deployState,
            AbstractConfigProducer<?> ancestor,
            List<Element> allChainsElems, Map<String, ComponentsBuilder.ComponentType<?>> outerComponentTypeByComponentName) {

        return new ChainsBuilder<>(deployState, ancestor, allChainsElems, outerComponentTypeByComponentName, chainType2BuilderClass);
    }
}

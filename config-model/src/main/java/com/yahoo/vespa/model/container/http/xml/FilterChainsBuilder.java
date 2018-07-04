// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.google.common.collect.ImmutableMap;
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

import java.util.*;

/**
 * @author Tony Vaagenes
 */
public class FilterChainsBuilder extends DomChainsBuilder<Filter, Chain<Filter>, FilterChains>  {
    private static final Collection<ComponentType<Filter>> allowedComponentTypes =
            Collections.singleton(ComponentType.filter);

    private static final Map<String, Class<? extends DomChainBuilderBase<? extends Filter, ? extends Chain<Filter>>>> chainType2BuilderClass =
            ImmutableMap.of("request-chain", FilterChainBuilder.class,
                            "response-chain", FilterChainBuilder.class);

    public FilterChainsBuilder() {
        super(null, allowedComponentTypes, null);
    }

    @Override
    protected FilterChains newChainsInstance(AbstractConfigProducer parent) {
        return new FilterChains(parent);
    }

    @Override
    protected ChainsBuilder<Filter, Chain<Filter>> readChains(
            AbstractConfigProducer ancestor,
            List<Element> allChainsElems, Map<String, ComponentsBuilder.ComponentType> outerComponentTypeByComponentName) {

        return new ChainsBuilder<>(ancestor, allChainsElems, outerComponentTypeByComponentName, chainType2BuilderClass);
    }
}

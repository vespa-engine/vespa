// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainBuilderBase;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.http.Filter;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder.ComponentType;

/**
 * @author Tony Vaagenes
 */
public class FilterChainBuilder extends DomChainBuilderBase<Filter, Chain<Filter>> {

    private static final Collection<ComponentType<Filter>> allowedComponentTypes = Collections.singleton(ComponentType.filter);

    public FilterChainBuilder(Map<String, ComponentType<?>> outerFilterTypeByComponentName) {
        super(allowedComponentTypes, outerFilterTypeByComponentName);
    }

    @Override
    protected Chain<Filter> buildChain(DeployState deployState, AbstractConfigProducer<?> ancestor, Element producerSpec, ChainSpecification specWithoutInnerComponents) {
        return new Chain<>(specWithoutInnerComponents);
    }
}

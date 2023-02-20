// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Base functionality for all chain builders (docprocChain, searchChain, provider, source)
 * @author Tony Vaagenes
 */
public abstract class DomChainBuilderBase<COMPONENT extends ChainedComponent<?>, CHAIN extends Chain<COMPONENT>>
    extends VespaDomBuilder.DomConfigProducerBuilderBase<CHAIN> {

    private final Collection<ComponentsBuilder.ComponentType<COMPONENT>> allowedComponentTypes;
    protected final Map<String, ComponentsBuilder.ComponentType<?>> outerComponentTypeByComponentName;

    public DomChainBuilderBase(Collection<ComponentsBuilder.ComponentType<COMPONENT>> allowedComponentTypes,
                               Map<String, ComponentsBuilder.ComponentType<?>> outerComponentTypeByComponentName) {
        this.allowedComponentTypes = allowedComponentTypes;
        this.outerComponentTypeByComponentName = outerComponentTypeByComponentName;
    }

    public final CHAIN doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element producerSpec) {
        ComponentsBuilder<COMPONENT> componentsBuilder =
                new ComponentsBuilder<>(deployState, ancestor, allowedComponentTypes, List.of(producerSpec), outerComponentTypeByComponentName);
        ChainSpecification specWithoutInnerComponents =
                new ChainSpecificationBuilder(producerSpec).build(componentsBuilder.getOuterComponentReferences());

        CHAIN chain = buildChain(deployState, ancestor, producerSpec, specWithoutInnerComponents);
        addInnerComponents(chain, componentsBuilder.getComponentDefinitions());

        return chain;
    }

    private void addInnerComponents(CHAIN chain, Collection<COMPONENT> componentDefinitions) {
        for (COMPONENT innerComponent : componentDefinitions) {
            chain.addInnerComponent(innerComponent);
        }
    }

    protected abstract CHAIN buildChain(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element producerSpec,
                                        ChainSpecification specWithoutInnerComponents);
}

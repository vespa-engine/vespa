// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder.ComponentType;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;
import com.yahoo.vespa.model.container.component.chain.Chains;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * NOTE: This class _must_ be abstract, due to calling subclass method in ctor.
 * @author Tony Vaagenes
 * @author gjoranv
 */
public abstract
class DomChainsBuilder<COMPONENT extends ChainedComponent<?>, CHAIN extends Chain<COMPONENT>, CHAINS extends Chains<CHAIN>>
        extends VespaDomBuilder.DomConfigProducerBuilder<CHAINS> {

    private final Collection<ComponentType<COMPONENT>> allowedComponentTypes;

    protected DomChainsBuilder(Collection<ComponentType<COMPONENT>> allowedComponentTypes) {

        this.allowedComponentTypes = new ArrayList<>(allowedComponentTypes);
    }

    protected abstract CHAINS newChainsInstance(AbstractConfigProducer<?> parent);

    @Override
    protected final CHAINS doBuild(DeployState deployState, AbstractConfigProducer<?> parent, Element chainsElement) {
        CHAINS chains = newChainsInstance(parent);

        List<Element> allChainElements = allChainElements(deployState, chainsElement);
        if (! allChainElements.isEmpty()) {
            ComponentsBuilder<COMPONENT> outerComponentsBuilder = readOuterComponents(deployState, chains, allChainElements);
            ChainsBuilder<COMPONENT, CHAIN> chainsBuilder = readChains(deployState, chains, allChainElements,
                                                                       outerComponentsBuilder.getComponentTypeByComponentName());

            addOuterComponents(chains, outerComponentsBuilder);
            addChains(chains, chainsBuilder);
        }
        return chains;
    }

    private List<Element> allChainElements(DeployState deployState, Element chainsElement) {
        List<Element> chainsElements = new ArrayList<>();
        chainsElements.add(chainsElement);

        return chainsElements;
    }

    private ComponentsBuilder<COMPONENT> readOuterComponents(DeployState deployState, AbstractConfigProducer<?> ancestor, List<Element> chainsElems) {
        return new ComponentsBuilder<>(deployState, ancestor, allowedComponentTypes, chainsElems, null);
    }

    protected abstract
    ChainsBuilder<COMPONENT, CHAIN> readChains(DeployState deployState, AbstractConfigProducer<?> ancestor, List<Element> allChainsElems,
                                               Map<String, ComponentsBuilder.ComponentType<?>> outerComponentTypeByComponentName);

    private void addOuterComponents(CHAINS chains, ComponentsBuilder<COMPONENT> outerComponentsBuilder) {
        assert (outerComponentsBuilder.getOuterComponentReferences().isEmpty());

        for (ChainedComponent<?> outerComponent : outerComponentsBuilder.getComponentDefinitions()) {
            chains.add(outerComponent);
        }
    }

    private void addChains(CHAINS chains, ChainsBuilder<COMPONENT, CHAIN> chainsBuilder) {
        for (CHAIN chain : chainsBuilder.getChains()) {
            chains.add(chain);
        }
    }
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.text.XML;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class ChainsBuilder<COMPONENT extends ChainedComponent<?>, CHAIN extends Chain<COMPONENT>> {

    protected final List<CHAIN> chains = new ArrayList<>();
    private final Map<String, Class<? extends DomChainBuilderBase<? extends COMPONENT, ? extends CHAIN>>> chainType2BuilderClass;

    // NOTE: The chain type string (key in chainType2BuilderClass) must match the xml tag name for the chain.
    public ChainsBuilder(DeployState deployState, AbstractConfigProducer<?> ancestor, List<Element> chainsElems,
                         Map<String, ComponentsBuilder.ComponentType<?>> outerComponentTypeByComponentName,
                         Map<String, Class<? extends DomChainBuilderBase<? extends COMPONENT, ? extends CHAIN>>> chainType2BuilderClass) {

        this.chainType2BuilderClass = chainType2BuilderClass;
        readChains(deployState, ancestor, chainsElems, outerComponentTypeByComponentName);
     }

    public Collection<CHAIN> getChains() {
        return Collections.unmodifiableCollection(chains);
    }

    private void readChains(DeployState deployState, AbstractConfigProducer<?> ancestor, List<Element> chainsElems,
                            Map<String, ComponentsBuilder.ComponentType<?>> outerSearcherTypeByComponentName) {

        for (Map.Entry<String, Class<? extends DomChainBuilderBase<? extends COMPONENT, ? extends CHAIN>>>
                chainType : chainType2BuilderClass.entrySet()) {
            for (Element elemContainingChainElems : chainsElems) {
                for (Element chainElem : XML.getChildren(elemContainingChainElems, chainType.getKey())) {
                    readChain(deployState, ancestor, chainElem, chainType.getValue(), outerSearcherTypeByComponentName);
                }
            }
        }
    }

    private void readChain(DeployState deployState, AbstractConfigProducer<?> ancestor, Element chainElem,
                           Class<? extends DomChainBuilderBase<? extends COMPONENT, ? extends CHAIN>> builderClass,
                           Map<String, ComponentsBuilder.ComponentType<?>> outerSearcherTypeByComponentName) {

        DomChainBuilderBase<? extends COMPONENT, ? extends CHAIN> builder =
                DomBuilderCreator.create(builderClass, outerSearcherTypeByComponentName);
        chains.add(builder.build(deployState, ancestor, chainElem));
    }

}

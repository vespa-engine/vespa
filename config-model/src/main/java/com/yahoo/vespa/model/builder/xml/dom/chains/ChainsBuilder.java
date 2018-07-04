// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import com.yahoo.text.XML;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;
import org.w3c.dom.Element;

import java.util.*;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class ChainsBuilder<COMPONENT extends ChainedComponent<?>, CHAIN extends Chain<COMPONENT>> {

    protected final List<CHAIN> chains = new ArrayList<>();
    private final Map<String, Class<? extends DomChainBuilderBase<? extends COMPONENT, ? extends CHAIN>>> chainType2BuilderClass;

    // NOTE: The chain type string (key in chainType2BuilderClass) must match the xml tag name for the chain.
    public ChainsBuilder(AbstractConfigProducer ancestor, List<Element> chainsElems,
                         Map<String, ComponentsBuilder.ComponentType> outerComponentTypeByComponentName,
                         Map<String, Class<? extends DomChainBuilderBase<? extends COMPONENT, ? extends CHAIN>>> chainType2BuilderClass) {

        this.chainType2BuilderClass = chainType2BuilderClass;
        readChains(ancestor, chainsElems, outerComponentTypeByComponentName);
     }

    public Collection<CHAIN> getChains() {
        return Collections.unmodifiableCollection(chains);
    }

    private void readChains(AbstractConfigProducer ancestor, List<Element> chainsElems,
                            Map<String, ComponentsBuilder.ComponentType> outerSearcherTypeByComponentName) {

        for (Map.Entry<String, Class<? extends DomChainBuilderBase<? extends COMPONENT, ? extends CHAIN>>>
                chainType : chainType2BuilderClass.entrySet()) {
            for (Element elemContainingChainElems : chainsElems) {
                for (Element chainElem : XML.getChildren(elemContainingChainElems, chainType.getKey())) {
                    readChain(ancestor, chainElem, chainType.getValue(), outerSearcherTypeByComponentName);
                }
            }
        }
    }

    private void readChain(AbstractConfigProducer ancestor, Element chainElem,
                           Class<? extends DomChainBuilderBase<? extends COMPONENT, ? extends CHAIN>> builderClass,
                           Map<String, ComponentsBuilder.ComponentType> outerSearcherTypeByComponentName) {

        DomChainBuilderBase<? extends COMPONENT, ? extends CHAIN> builder =
                DomBuilderCreator.create(builderClass, outerSearcherTypeByComponentName);
        chains.add(builder.build(ancestor, chainElem));
    }

}

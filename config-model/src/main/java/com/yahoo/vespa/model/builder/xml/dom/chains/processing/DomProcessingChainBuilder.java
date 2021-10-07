// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.processing;

import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainBuilderBase;
import com.yahoo.vespa.model.container.processing.ProcessingChain;
import com.yahoo.vespa.model.container.processing.Processor;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Map;

/**
 * @author bratseth
 * @since   5.1.6
 */
public class DomProcessingChainBuilder extends DomChainBuilderBase<Processor, ProcessingChain> {

    public DomProcessingChainBuilder(Map<String, ComponentsBuilder.ComponentType> outerComponentTypeByComponentName) {
        super(Arrays.asList(ComponentsBuilder.ComponentType.processor), outerComponentTypeByComponentName);
    }

    protected ProcessingChain buildChain(DeployState deployState, AbstractConfigProducer ancestor, Element producerSpec,
                                         ChainSpecification specWithoutInnerComponents) {
        return new ProcessingChain(specWithoutInnerComponents);
    }


}

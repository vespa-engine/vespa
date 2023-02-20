// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.processing;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainsBuilder;
import com.yahoo.vespa.model.container.processing.ProcessingChain;
import com.yahoo.vespa.model.container.processing.ProcessingChains;
import com.yahoo.vespa.model.container.processing.Processor;
import org.w3c.dom.Element;
import java.util.List;
import java.util.Map;

/**
 * Root builder of the processing model
 *
 * @author  bratseth
 */
public class DomProcessingBuilder extends DomChainsBuilder<Processor, ProcessingChain, ProcessingChains> {

    public DomProcessingBuilder(Element outerChainsElem) {
        super(List.of(ComponentsBuilder.ComponentType.processor));
    }

    @Override
    protected ProcessingChains newChainsInstance(TreeConfigProducer<AnyConfigProducer> parent) {
        return new ProcessingChains(parent, "processing");
    }

    @Override
    protected ProcessingChainsBuilder readChains(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, List<Element> processingChainsElements,
                                                 Map<String, ComponentsBuilder.ComponentType<?>> outerComponentTypeByComponentName) {
        return new ProcessingChainsBuilder(deployState, ancestor, processingChainsElements, outerComponentTypeByComponentName);
    }

}

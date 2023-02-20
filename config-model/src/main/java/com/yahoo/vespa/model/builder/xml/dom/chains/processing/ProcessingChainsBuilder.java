// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.processing;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.chains.ChainsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainBuilderBase;
import com.yahoo.vespa.model.container.processing.ProcessingChain;
import com.yahoo.vespa.model.container.processing.Processor;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Creates all processing chains from xml.
 *
 * @author bratseth
 */
public class ProcessingChainsBuilder extends ChainsBuilder<Processor, ProcessingChain> {

    private static final Map<String, Class<? extends DomChainBuilderBase<? extends Processor, ? extends ProcessingChain>>>
            chainType2builderClass = Collections.unmodifiableMap(
            new LinkedHashMap<String, Class<? extends DomChainBuilderBase<? extends Processor, ? extends ProcessingChain>>>() {{
                put("chain", DomProcessingChainBuilder.class);
            }});

    public ProcessingChainsBuilder(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, List<Element> processingChainsElements,
                                   Map<String, ComponentsBuilder.ComponentType<?>> outerSearcherTypeByComponentName) {
        super(deployState, ancestor, processingChainsElements, outerSearcherTypeByComponentName, chainType2builderClass);
    }

}

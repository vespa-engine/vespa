// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.processing;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.builder.xml.dom.chains.ChainedComponentModelBuilder;
import com.yahoo.vespa.model.container.processing.Processor;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import org.w3c.dom.Element;

/**
 * Builds a processor from XML.
 *
 * @author  bratseth
 * @since   5.1.6
 */
public class DomProcessorBuilder extends VespaDomBuilder.DomConfigProducerBuilderBase<Processor> {

    @Override
    protected Processor doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element processorElement) {
        ChainedComponentModelBuilder modelBuilder = new ChainedComponentModelBuilder(processorElement);
        return new Processor(modelBuilder.build());
    }

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.docproc;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.docproc.DocumentProcessor;
import org.w3c.dom.Element;

/**
 * Builds a DocumentProcessor from XML.
 *
 * @author gjoranv
 */
public class DomDocumentProcessorBuilder extends VespaDomBuilder.DomConfigProducerBuilderBase<DocumentProcessor> {

    @Override
    protected DocumentProcessor doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element documentProcessorElement) {
        DocumentProcessorModelBuilder modelBuilder = new DocumentProcessorModelBuilder(documentProcessorElement);
        return new DocumentProcessor(modelBuilder.build());
    }

}

// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.docproc;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.docproc.DocumentProcessor;
import org.w3c.dom.Element;

/**
 * Builds a DocumentProcessor from XML.
 *
 * @author gjoranv
 */
public class DomDocumentProcessorBuilder extends VespaDomBuilder.DomConfigProducerBuilder<DocumentProcessor> {

    protected DocumentProcessor doBuild(AbstractConfigProducer ancestor, Element documentProcessorElement) {
        DocumentProcessorModelBuilder modelBuilder = new DocumentProcessorModelBuilder(documentProcessorElement);
        return new DocumentProcessor(modelBuilder.build());
    }

}

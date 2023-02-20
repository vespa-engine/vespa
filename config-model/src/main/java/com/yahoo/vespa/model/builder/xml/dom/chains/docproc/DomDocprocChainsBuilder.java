// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.docproc;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder.ComponentType;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainsBuilder;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.container.docproc.DocprocChains;
import com.yahoo.vespa.model.container.docproc.DocumentProcessor;
import org.w3c.dom.Element;
import java.util.List;
import java.util.Map;

/**
 * Builds the docproc chains model from xml.
 *
 * @author gjoranv
 */
public class DomDocprocChainsBuilder  extends DomChainsBuilder<DocumentProcessor, DocprocChain, DocprocChains> {
    public DomDocprocChainsBuilder(Element outerChainsElem, boolean supportDocprocChainsDir) {
        super(List.of(ComponentType.documentprocessor)
        );
    }

    @Override
    protected DocprocChains newChainsInstance(TreeConfigProducer<AnyConfigProducer> parent) {
        return new DocprocChains(parent, "docprocchains");
    }

    @Override
    protected DocprocChainsBuilder readChains(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, List<Element> docprocChainsElements,
                                              Map<String, ComponentType<?>> outerComponentTypeByComponentName) {
        return new DocprocChainsBuilder(deployState, ancestor, docprocChainsElements, outerComponentTypeByComponentName);
    }
}

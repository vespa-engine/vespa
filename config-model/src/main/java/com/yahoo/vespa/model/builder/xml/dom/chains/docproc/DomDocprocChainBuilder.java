// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.docproc;

import com.yahoo.collections.Pair;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainBuilderBase;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.container.docproc.DocumentProcessor;
import org.w3c.dom.Element;
import java.util.List;
import java.util.Map;

/**
 * Builds a docproc chain from xml
 *
 * @author gjoranv
 */
public class DomDocprocChainBuilder extends DomChainBuilderBase<DocumentProcessor, DocprocChain> {

    public DomDocprocChainBuilder(Map<String, ComponentsBuilder.ComponentType<?>> outerComponentTypeByComponentName) {
        super(List.of(ComponentsBuilder.ComponentType.documentprocessor), outerComponentTypeByComponentName);
    }

    @Override
    protected DocprocChain buildChain(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element producerSpec,
                                      ChainSpecification specWithoutInnerComponents) {
        Map<Pair<String, String>, String> fieldNameSchemaMap = DocumentProcessorModelBuilder.parseFieldNameSchemaMap(producerSpec);
        return new DocprocChain(specWithoutInnerComponents, fieldNameSchemaMap);
    }

}

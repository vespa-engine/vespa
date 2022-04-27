// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.docproc;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.chains.ChainsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainBuilderBase;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.container.docproc.DocumentProcessor;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates all docproc chains from xml.
 *
 * @author gjoranv
 */
public class DocprocChainsBuilder extends ChainsBuilder<DocumentProcessor, DocprocChain> {

    private static final Map<String, Class<? extends DomChainBuilderBase<? extends DocumentProcessor, ? extends DocprocChain>>>
            chainType2builderClass = Collections.unmodifiableMap(
            new LinkedHashMap<>() {{
                put("docprocchain", DomDocprocChainBuilder.class);
                put("chain", DomDocprocChainBuilder.class);
            }});

    public DocprocChainsBuilder(DeployState deployState, AbstractConfigProducer<?> ancestor, List<Element> docprocChainsElements,
                                Map<String, ComponentsBuilder.ComponentType<?>> outerSearcherTypeByComponentName) {
        super(deployState, ancestor, docprocChainsElements, outerSearcherTypeByComponentName, chainType2builderClass);
    }

}

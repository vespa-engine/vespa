// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.processing.test;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.vespa.model.builder.xml.dom.chains.processing.DomProcessingBuilder;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;
import com.yahoo.vespa.model.container.component.chain.Chains;
import com.yahoo.vespa.model.container.processing.ProcessingChain;
import com.yahoo.vespa.model.container.processing.Processor;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.Collection;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 * @author gjoranv
 */
public class ProcessingChainsTest extends DomBuilderTest {

    private Chains<ProcessingChain> processingChains;

    @Before
    public void setupProcessingChains() {
        DomProcessingBuilder processingBuilder = new DomProcessingBuilder(null);
        processingBuilder.build(root, servicesXml());
        processingChains = (Chains<ProcessingChain>)root.getChildren().get("processing");
    }

    private Element servicesXml() {
        return parse(
                "<processing>",
                "  <processor id='processor1' class='com.yahoo.test.Processor1' />",
                "  <renderer id='renderer1' class='com.yahoo.renderer.Renderer'/>",
                "  <chain id='default'>",
                "    <processor idref='processor1'/>",
                "    <processor id='processor2' class='com.yahoo.test.Processor2'/>",
                "  </chain>",
                "</processing>");
    }

    @Test
    public void testProcessingChainConfiguration() {
        ProcessingChain defaultChain = processingChains.allChains().getComponent("default");
        assertEquals("default", defaultChain.getId().stringValue());
        assertEquals(1, defaultChain.getInnerComponents().size());

        Collection<ChainedComponent<?>> outerProcessors = processingChains.getComponentGroup().getComponents();
        assertEquals(1, outerProcessors.size());
        assertEquals("processor1", outerProcessors.iterator().next().getComponentId().toString());

        Collection<Processor> innerProcessors = defaultChain.getInnerComponents();
        assertEquals("processor2", innerProcessors.iterator().next().getComponentId().toString());
    }

    @Test
    public void require_that_processors_have_correct_class() {
        ChainedComponent<?> processor1 = processingChains.getComponentGroup().getComponents().iterator().next();
        assertEquals("com.yahoo.test.Processor1", processor1.model.bundleInstantiationSpec.classId.stringValue());

        ProcessingChain defaultChain = processingChains.allChains().getComponent("default");
        Processor processor2 = defaultChain.getInnerComponents().iterator().next();
        assertEquals("com.yahoo.test.Processor2", processor2.model.bundleInstantiationSpec.classId.stringValue());
    }

}

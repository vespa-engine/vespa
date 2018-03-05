// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author tonytv
 */
public class DomSearcherBuilderTest extends DomBuilderTest {

    @Test
    public void ensureCorrectModel() {
        ChainedComponent<ChainedComponentModel> searcher = new DomSearcherBuilder().doBuild(root, parse(
                "<searcher id='theId' class='theclassid' bundle='thebundle' provides='p1'>",
                "    <provides>p2</provides>",
                "</searcher>"));

        ChainedComponentModel model = searcher.model;
        assertEquals(2, model.dependencies.provides().size());

        BundleInstantiationSpecification instantiationSpecification = model.bundleInstantiationSpec;
        assertEquals("theId", instantiationSpecification.id.stringValue());
        assertEquals("theclassid", instantiationSpecification.classId.stringValue());
        assertEquals("thebundle", instantiationSpecification.bundle.stringValue());
    }

}

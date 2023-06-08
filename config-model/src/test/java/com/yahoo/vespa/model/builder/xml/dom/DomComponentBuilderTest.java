// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.vespa.model.container.component.Component;
import org.junit.jupiter.api.Test;

import static com.yahoo.collections.CollectionUtil.first;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author gjoranv
 */
public class DomComponentBuilderTest extends DomBuilderTest {

    @Test
    void ensureCorrectModel() {
        Component<?, ?> handler = new DomComponentBuilder().doBuild(root.getDeployState(), root, parse(
                "<handler id='theId' class='theClass' bundle='theBundle' />"));

        BundleInstantiationSpecification instantiationSpecification = handler.model.bundleInstantiationSpec;
        assertEquals("theId", instantiationSpecification.id.stringValue());
        assertEquals("theClass", instantiationSpecification.classId.stringValue());
        assertEquals("theBundle", instantiationSpecification.bundle.stringValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void components_can_be_nested() {
        Component<? super Component<?, ?>, ?> parent = new DomComponentBuilder().doBuild(root.getDeployState(), root, parse(
                "<component id='parent'>",
                "  <component id='child' />",
                "</component>"));

        assertEquals(ComponentId.fromString("parent"), parent.getGlobalComponentId());
        Component<?, ?> child = (Component<?, ?>) first(parent.getChildren().values());
        assertNotNull(child);

        assertEquals(ComponentId.fromString("child@parent"), child.getGlobalComponentId());
    }
}

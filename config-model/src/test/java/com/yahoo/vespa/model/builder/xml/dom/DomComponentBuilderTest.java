// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.vespa.model.container.component.Component;
import org.junit.Test;

import static com.yahoo.collections.CollectionUtil.first;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 */
public class DomComponentBuilderTest extends DomBuilderTest {

    @Test
    public void ensureCorrectModel() {
        Component<?, ?> handler = new DomComponentBuilder().doBuild(root.getDeployState(), root, parse(
                "<handler id='theId' class='theClass' bundle='theBundle' />"));

        BundleInstantiationSpecification instantiationSpecification = handler.model.bundleInstantiationSpec;
        assertThat(instantiationSpecification.id.stringValue(), is("theId"));
        assertThat(instantiationSpecification.classId.stringValue(), is("theClass"));
        assertThat(instantiationSpecification.bundle.stringValue(), is("theBundle"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void components_can_be_nested() {
        Component<Component<?, ?>, ?> parent = new DomComponentBuilder().doBuild(root.getDeployState(), root, parse(
                "<component id='parent'>",
                "  <component id='child' />",
                "</component>"));

        assertThat(parent.getGlobalComponentId(), is(ComponentId.fromString("parent")));
        Component<?, ?> child = first(parent.getChildren().values());
        assertNotNull(child);

        assertThat(child.getGlobalComponentId(), is(ComponentId.fromString("child@parent")));
    }
}

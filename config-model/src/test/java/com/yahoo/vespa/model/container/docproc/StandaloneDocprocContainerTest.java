// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.docproc;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder.Networking;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Einar M R Rosenvinge
 */
public class StandaloneDocprocContainerTest extends DomBuilderTest {

    public ContainerCluster setupCluster(boolean standalone) {
        ContainerModelBuilder builder = new ContainerModelBuilder(standalone, Networking.disable);
        ContainerModel model = builder.build(DeployState.createTestState(), null, null, root, servicesXml());

        if (!standalone)
            model.getCluster().getDocproc().getChains().addServersAndClientsForChains();

        root.freezeModelTopology();
        return model.getCluster();
    }

    private Element servicesXml() {
        return parse("" +
                "<container version=\"1.0\">\n" +
                "    <document-processing>\n" +
                "        <chain id=\"foo\">\n" +
                "            <documentprocessor id=\"MyDocproc\"/>\n" +
                "        </chain>\n" +
                "    </document-processing>\n" +
                "    <nodes>\n" +
                "        <node hostalias=\"node01\"/>\n" +
                "    </nodes>\n" +
                "</container>\n");
    }

    @Test
    public void requireMbusProvidersWhenNonStandalone() {
        ContainerCluster containerCluster = setupCluster(false);
        Map<ComponentId, Component<?, ?>> components = containerCluster.getComponentsMap();

        boolean foundAtLeastOneClient = false;
        boolean foundAtLeastOneServer = false;

        for (ComponentId componentId : components.keySet()) {
            if (componentId.stringValue().contains("MbusClient")) foundAtLeastOneClient = true;
            if (componentId.stringValue().contains("MbusServer")) foundAtLeastOneServer = true;
        }
        assertThat(foundAtLeastOneClient, is(true));
        assertThat(foundAtLeastOneServer, is(true));

    }

    @Test
    public void requireNoMbusProvidersWhenStandalone() {
        ContainerCluster containerCluster = setupCluster(true);
        Map<ComponentId, Component<?, ?>> components = containerCluster.getComponentsMap();

        boolean foundAtLeastOneClient = false;
        boolean foundAtLeastOneServer = false;

        for (ComponentId componentId : components.keySet()) {
            if (componentId.stringValue().contains("MbusClient")) foundAtLeastOneClient = true;
            if (componentId.stringValue().contains("MbusServer")) foundAtLeastOneServer = true;
        }
        assertThat(foundAtLeastOneClient, is(false));
        assertThat(foundAtLeastOneServer, is(false));
    }
}

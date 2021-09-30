// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import org.junit.Test;
import org.w3c.dom.Element;

import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class StrictFilteringTest extends DomBuilderTest {

    @Test
    public void default_request_and_response_filters_in_services_xml_are_listen_in_server_config() {
        Element xml = parse(
                "<container version='1.0'>",
                "  <http>",
                "    <filtering strict-mode=\"true\">",
                "      <request-chain id='request-chain-with-binding'>",
                "        <filter id='my-filter' class='MyFilter'/>",
                "        <binding>http://*/my-chain-binding</binding>",
                "      </request-chain>",
                "    </filtering>",
                "    <server id='server1' port='8000' />",
                "  </http>",
                "</container>");
        buildContainerCluster(xml);
        ServerConfig config = root.getConfig(ServerConfig.class, "container/http/jdisc-jetty/server1");
        assertTrue(config.strictFiltering());
    }

    private void buildContainerCluster(Element containerElem) {
        new ContainerModelBuilder(true, ContainerModelBuilder.Networking.enable).build(DeployState.createTestState(), null, null, root, containerElem);
        root.freezeModelTopology();
    }
}

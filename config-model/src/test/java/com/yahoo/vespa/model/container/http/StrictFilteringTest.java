// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class StrictFilteringTest extends DomBuilderTest {

    @Test
    void strict_filtering_enabled_if_specified_in_services() {
        Element xml = parse(
                "<container version='1.0'>",
                "  <http>",
                "    <filtering strict-mode='true'>",
                "      <request-chain id='request-chain-with-binding'>",
                "        <filter id='my-filter' class='MyFilter'/>",
                "        <binding>http://*/my-chain-binding</binding>",
                "      </request-chain>",
                "    </filtering>",
                "    <server id='server1' port='8000' />",
                "  </http>",
                "</container>");
        assertStrictFiltering(true, xml);
    }

    @Test
    void strict_filtering_enabled_by_default_if_filter_present() {
        Element xml = parse(
                "<container version='1.0'>",
                "  <http>",
                "    <filtering>",
                "      <request-chain id='request-chain'>",
                "        <filter id='my-filter' class='MyFilter'/>",
                "      </request-chain>",
                "    </filtering>",
                "    <server id='server1' port='8000' />",
                "  </http>",
                "</container>");
        assertStrictFiltering(true, xml);
    }

    @Test
    void strict_filtering_disabled_if_no_filter() {
        Element xml = parse(
                "<container version='1.0'>",
                "  <http>",
                "    <filtering>",
                "    </filtering>",
                "    <server id='server1' port='8000' />",
                "  </http>",
                "</container>");
        assertStrictFiltering(false, xml);
    }

    private void assertStrictFiltering(boolean expected, Element services) {
        buildContainerCluster(services);
        ServerConfig config = root.getConfig(ServerConfig.class, "container/http/jdisc-jetty/server1");
        assertEquals(expected, config.strictFiltering());
    }

    private void buildContainerCluster(Element containerElem) {
        new ContainerModelBuilder(true, ContainerModelBuilder.Networking.enable).build(DeployState.createTestState(), null, null, root, containerElem);
        root.freezeModelTopology();
    }
}

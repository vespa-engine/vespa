// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.UserBindingPattern;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author bjorncs
 */
public class DefaultFilterTest extends DomBuilderTest {

    private void buildContainerCluster(Element containerElem) {
        new ContainerModelBuilder(true, ContainerModelBuilder.Networking.enable).build(DeployState.createTestState(), null, null, root, containerElem);
        root.freezeModelTopology();
    }

    @Test
    void default_request_and_response_filters_in_services_xml_are_listen_in_server_config() {
        BindingPattern binding = UserBindingPattern.fromHttpPath("/my-chain-binding");
        Element xml = parse(
                "<container version='1.0'>",
                "  <http>",
                "    <filtering>",
                "      <request-chain id='request-chain-with-binding'>",
                "        <binding>" + binding.patternString() + "</binding>",
                "      </request-chain>",
                "      <response-chain id='response-chain-with-binding'>",
                "        <binding>" + binding.patternString() + "</binding>",
                "      </response-chain>",
                "      <request-chain id='my-default-request-chain'/>" +
                        "      <response-chain id='my-default-response-chain'/>",
                "    </filtering>",
                "    <server id='server1' port='8000' default-request-chain=\"my-default-request-chain\" default-response-chain=\"my-default-response-chain\"/>",
                "    <server id='server2' port='9000' />",
                "  </http>",
                "</container>");
        buildContainerCluster(xml);

        assertDefaultFiltersInConfig(root.getConfig(ServerConfig.class, "container/http/jdisc-jetty/server1"));
        assertDefaultFiltersInConfig(root.getConfig(ServerConfig.class, "container/http/jdisc-jetty/server2"));

        ChainsConfig chainsConfig = root.getConfig(ChainsConfig.class, "container/filters/chain");
        Set<String> chainsIds = chainsConfig.chains().stream().map(ChainsConfig.Chains::id).collect(toSet());
        assertThat(chainsIds)
                .containsExactlyInAnyOrder(
                        "request-chain-with-binding", "response-chain-with-binding", "my-default-request-chain", "my-default-response-chain");
    }

    private static void assertDefaultFiltersInConfig(ServerConfig config) {
        assertThat(config.defaultFilters())
                .containsExactlyInAnyOrder(
                        new ServerConfig.DefaultFilters(new ServerConfig.DefaultFilters.Builder()
                                .filterId("my-default-request-chain").localPort(8000)),
                        new ServerConfig.DefaultFilters(new ServerConfig.DefaultFilters.Builder()
                                .filterId("my-default-response-chain").localPort(8000)));
    }
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.UserBindingPattern;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.http.xml.HttpBuilder;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder.Networking;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static com.yahoo.collections.CollectionUtil.first;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author gjoranv
 */
public class FilterBindingsTest extends DomBuilderTest {

    private static final BindingPattern MY_CHAIN_BINDING = UserBindingPattern.fromHttpPath("/my-chain-binding");

    private Http buildHttp(Element xml) {
        Http http = new HttpBuilder().build(root.getDeployState(), root, xml);
        root.freezeModelTopology();
        http.validate();
        return http;
    }


    private void buildContainerCluster(Element containerElem) {
        ContainerModel model = new ContainerModelBuilder(true, Networking.enable).build(DeployState.createTestState(), null, null, root, containerElem);
        root.freezeModelTopology();
    }

    @Test
    void request_chain_binding_is_added_to_http() throws Exception {
        Element xml = parse(
                "<http>",
                "  <filtering>",
                "    <request-chain id='my-request-chain'>",
                "      <binding>" + MY_CHAIN_BINDING.patternString() + "</binding>",
                "    </request-chain>",
                "  </filtering>",
                "</http>");
        Http http = buildHttp(xml);

        FilterBinding binding = first(http.getBindings());
        assertEquals("my-request-chain", binding.chainId().getName());
        assertEquals(MY_CHAIN_BINDING, binding.binding());

        Chain<Filter> myChain = http.getFilterChains().allChains().getComponent("my-request-chain");
        assertNotNull(myChain, "Missing chain");
    }

    @Test
    void response_chain_binding_is_added_to_http() throws Exception {
        Element xml = parse(
                "<http>",
                "  <filtering>",
                "    <response-chain id='my-response-chain'>",
                "      <binding>" + MY_CHAIN_BINDING.patternString() + "</binding>",
                "    </response-chain>",
                "  </filtering>",
                "</http>");
        Http http = buildHttp(xml);

        FilterBinding binding = first(http.getBindings());
        assertEquals("my-response-chain", binding.chainId().getName());
        assertEquals(MY_CHAIN_BINDING, binding.binding());

        Chain<Filter> myChain = http.getFilterChains().allChains().getComponent("my-response-chain");
        assertNotNull(myChain, "Missing chain");
    }

    @Test
    void bindings_are_added_to_config_for_all_http_servers_with_jetty() {
        final Element xml = parse(
                "<container version='1.0'>",
                "  <http>",
                "    <filtering>",
                "      <request-chain id='my-request-chain'>",
                "        <binding>" + MY_CHAIN_BINDING.patternString() + "</binding>",
                "      </request-chain>",
                "    </filtering>",
                "    <server id='server1' port='8000' />",
                "    <server id='server2' port='9000' />",
                "  </http>",
                "</container>");
        buildContainerCluster(xml);

        {
            final ServerConfig config = root.getConfig(ServerConfig.class, "container/http/jdisc-jetty/server1");
            assertEquals(1, config.filter().size());
            assertEquals("my-request-chain", config.filter(0).id());
            assertEquals(MY_CHAIN_BINDING.patternString(), config.filter(0).binding());
        }
        {
            final ServerConfig config = root.getConfig(ServerConfig.class, "container/http/jdisc-jetty/server2");
            assertEquals(1, config.filter().size());
            assertEquals("my-request-chain", config.filter(0).id());
            assertEquals(MY_CHAIN_BINDING.patternString(), config.filter(0).binding());
        }
    }

}

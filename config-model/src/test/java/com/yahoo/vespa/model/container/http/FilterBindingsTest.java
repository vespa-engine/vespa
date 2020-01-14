// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.http.xml.HttpBuilder;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder.Networking;
import org.junit.Test;
import org.w3c.dom.Element;

import static com.yahoo.collections.CollectionUtil.first;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

/**
 * @author gjoranv
 */
public class FilterBindingsTest extends DomBuilderTest {

    private static final String MY_CHAIN_BINDING = "http://*/my-chain-binding";

    private Http buildHttp(Element xml) throws Exception {
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
    public void request_chain_binding_is_added_to_http() throws Exception {
        Element xml = parse(
                "<http>",
                "  <filtering>",
                "    <request-chain id='my-request-chain'>",
                "      <binding>" + MY_CHAIN_BINDING + "</binding>",
                "    </request-chain>",
                "  </filtering>",
                "</http>");
        Http http = buildHttp(xml);

        Binding binding = first(http.getBindings());
        assertThat(binding.filterId().getName(), is("my-request-chain"));
        assertThat(binding.binding(), is(MY_CHAIN_BINDING));

        Chain<Filter> myChain = http.getFilterChains().allChains().getComponent("my-request-chain");
        assertNotNull("Missing chain", myChain);
    }

    @Test
    public void response_chain_binding_is_added_to_http() throws Exception {
        Element xml = parse(
                "<http>",
                "  <filtering>",
                "    <response-chain id='my-response-chain'>",
                "      <binding>" + MY_CHAIN_BINDING + "</binding>",
                "    </response-chain>",
                "  </filtering>",
                "</http>");
        Http http = buildHttp(xml);

        Binding binding = first(http.getBindings());
        assertThat(binding.filterId().getName(), is("my-response-chain"));
        assertThat(binding.binding(), is(MY_CHAIN_BINDING));

        Chain<Filter> myChain = http.getFilterChains().allChains().getComponent("my-response-chain");
        assertNotNull("Missing chain", myChain);
    }

    @Test
    public void bindings_are_added_to_config_for_all_http_servers_with_jetty() throws Exception {
        final Element xml = parse(
                "<container version='1.0'>",
                "  <http>",
                "    <filtering>",
                "      <request-chain id='my-request-chain'>",
                "        <binding>" + MY_CHAIN_BINDING + "</binding>",
                "      </request-chain>",
                "    </filtering>",
                "    <server id='server1' port='8000' />",
                "    <server id='server2' port='9000' />",
                "  </http>",
                "</container>");
        buildContainerCluster(xml);

        {
            final ServerConfig config = root.getConfig(ServerConfig.class, "container/http/jdisc-jetty/server1");
            assertThat(config.filter().size(), is(1));
            assertThat(config.filter(0).id(), is("my-request-chain"));
            assertThat(config.filter(0).binding(), is(MY_CHAIN_BINDING));
        }
        {
            final ServerConfig config = root.getConfig(ServerConfig.class, "container/http/jdisc-jetty/server2");
            assertThat(config.filter().size(), is(1));
            assertThat(config.filter(0).id(), is("my-request-chain"));
            assertThat(config.filter(0).binding(), is(MY_CHAIN_BINDING));
        }
    }

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.provision.Host;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.net.HostName;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ThreadPoolExecutorComponent;
import com.yahoo.vespa.model.container.component.Handler;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Einar M R Rosenvinge
 */
public class ContainerDocumentApiBuilderTest extends ContainerModelBuilderTestBase {

    private Map<String, Handler<?>> getHandlers(String clusterName) {
        ContainerCluster<?> cluster = (ContainerCluster<?>) root.getChildren().get(clusterName);
        Map<String, Handler<?>> handlerMap = new HashMap<>();
        Collection<Handler<?>> handlers = cluster.getHandlers();
        for (Handler<?> handler : handlers) {
            assertThat(handlerMap.containsKey(handler.getComponentId().toString()), is(false));  //die on overwrites
            handlerMap.put(handler.getComponentId().toString(), handler);
        }
        return handlerMap;
    }

    @Test
    public void custom_bindings_are_allowed() {
        Element elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                "  <document-api>",
                "    <binding>http://*/document-api/</binding>",
                "    <binding>missing-trailing-slash</binding>",
                "  </document-api>",
                nodesXml,
                "</container>");
        createModel(root, elem);

        verifyCustomBindings("com.yahoo.vespa.http.server.FeedHandler", ContainerCluster.RESERVED_URI_PREFIX + "/feedapi");
    }

    private void verifyCustomBindings(String id, String bindingSuffix) {
        Handler<?> handler = getHandlers("cluster1").get(id);

        assertThat(handler.getServerBindings(), hasItem("http://*/document-api/" + bindingSuffix));
        assertThat(handler.getServerBindings(), hasItem("http://*/document-api/" + bindingSuffix + "/"));
        assertThat(handler.getServerBindings(), hasItem("missing-trailing-slash/" + bindingSuffix));
        assertThat(handler.getServerBindings(), hasItem("missing-trailing-slash/" + bindingSuffix + "/"));

        assertThat(handler.getServerBindings().size(), is(4));
    }

    @Test
    public void requireThatHandlersAreSetup() {
        Element elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                "  <document-api />",
                nodesXml,
                "</container>");
        createModel(root, elem);

        Map<String, Handler<?>> handlerMap = getHandlers("cluster1");

        assertThat(handlerMap.get("com.yahoo.container.handler.VipStatusHandler"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.container.handler.observability.ApplicationStatusHandler"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.container.jdisc.state.StateHandler"), not(nullValue()));

        assertThat(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings().contains("http://*/" + ContainerCluster.RESERVED_URI_PREFIX + "/feedapi"), is(true));
        assertThat(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings().contains("http://*/" + ContainerCluster.RESERVED_URI_PREFIX + "/feedapi/"), is(true));
        assertThat(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings().size(), equalTo(2));
    }

    @Test
    public void feeding_api_have_separate_threadpools() {
        Element elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                "  <document-api />",
                nodesXml,
                "</container>");
        root = new MockRoot("root", new MockApplicationPackage.Builder().build(), new HostProvisionerWithCustomRealResource());
        createModel(root, elem);
        Map<String, Handler<?>> handlers = getHandlers("cluster1");
        Handler<?> feedApiHandler = handlers.get("com.yahoo.vespa.http.server.FeedHandler");
        List<ThreadPoolExecutorComponent> feedApiExecutors = feedApiHandler.getChildrenByTypeRecursive(ThreadPoolExecutorComponent.class);
        assertThat(feedApiExecutors, hasSize(1));
        ThreadpoolConfig.Builder configBuilder = new ThreadpoolConfig.Builder();
        feedApiExecutors.get(0).getConfig(configBuilder);
        ThreadpoolConfig config = new ThreadpoolConfig(configBuilder);
        assertEquals(4, config.maxthreads());
        assertEquals(4, config.corePoolSize());
    }

    private static class HostProvisionerWithCustomRealResource implements HostProvisioner {

        @Override
        public HostSpec allocateHost(String alias) {
            Host host = new Host(HostName.getLocalhost());
            ClusterMembership membership = ClusterMembership.from(
                    ClusterSpec
                            .specification(
                                    ClusterSpec.Type.container,
                                    ClusterSpec.Id.from("id"))
                            .vespaVersion("")
                            .group(ClusterSpec.Group.from(0))
                            .build(),
                    0);
            return new HostSpec(
                    host.hostname(), new NodeResources(4, 0, 0, 0), NodeResources.unspecified(), NodeResources.unspecified(),
                    membership, Optional.empty(), Optional.empty(), Optional.empty());
        }

        @Override public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) { return List.of(); }
    }

}

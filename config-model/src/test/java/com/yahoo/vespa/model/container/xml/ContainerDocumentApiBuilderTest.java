// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.HostProvisionerWithCustomRealResource;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.UserBindingPattern;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
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
                "  </document-api>",
                nodesXml,
                "</container>");
        createModel(root, elem);

        verifyCustomBindings("com.yahoo.vespa.http.server.FeedHandler");
    }

    private void verifyCustomBindings(String id) {
        Handler<?> handler = getHandlers("cluster1").get(id);

        assertThat(handler.getServerBindings(), hasItem(UserBindingPattern.fromHttpPath("/document-api/reserved-for-internal-use/feedapi")));
        assertThat(handler.getServerBindings(), hasItem(UserBindingPattern.fromHttpPath("/document-api/reserved-for-internal-use/feedapi/")));

        assertThat(handler.getServerBindings().size(), is(2));
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
        assertThat(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings()
                .contains(SystemBindingPattern.fromHttpPath("/reserved-for-internal-use/feedapi")),
                is(true));
        assertThat(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings()
                .contains(SystemBindingPattern.fromHttpPath("/reserved-for-internal-use/feedapi")),
                is(true));
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
        Set<String> injectedComponentIds = feedApiHandler.getInjectedComponentIds();
        assertThat(injectedComponentIds, hasItem("threadpool@feedapi-handler"));

        ContainerThreadpoolConfig config = root.getConfig(
                ContainerThreadpoolConfig.class, "cluster1/component/com.yahoo.vespa.http.server.FeedHandler/threadpool@feedapi-handler");
        assertEquals(16, config.maxThreads());
        assertEquals(8, config.minThreads());
    }

    @Test
    public void threadpools_configuration_can_be_overridden() {
        Element elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                "  <document-api>",
                "    <http-client-api>",
                "      <threadpool>",
                "        <max-threads>50</max-threads>",
                "        <min-threads>25</min-threads>",
                "        <queue-size>1000</queue-size>",
                "      </threadpool>",
                "    </http-client-api>",
                "  </document-api>",
                nodesXml,
                "</container>");
        createModel(root, elem);

        ContainerThreadpoolConfig feedThreadpoolConfig = root.getConfig(
                ContainerThreadpoolConfig.class, "cluster1/component/com.yahoo.vespa.http.server.FeedHandler/threadpool@feedapi-handler");
        assertEquals(50, feedThreadpoolConfig.maxThreads());
        assertEquals(25, feedThreadpoolConfig.minThreads());
        assertEquals(1000, feedThreadpoolConfig.queueSize());
    }

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.UserBindingPattern;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ContainerDocumentApiBuilderTest extends ContainerModelBuilderTestBase {

    private Map<String, Handler> getHandlers(String clusterName) {
        ContainerCluster<?> cluster = (ContainerCluster<?>) root.getChildren().get(clusterName);
        Map<String, Handler> handlerMap = new HashMap<>();
        Collection<Handler> handlers = cluster.getHandlers();
        for (Handler handler : handlers) {
            assertFalse(handlerMap.containsKey(handler.getComponentId().toString()));  //die on overwrites
            handlerMap.put(handler.getComponentId().toString(), handler);
        }
        return handlerMap;
    }

    @Test
    void custom_bindings_are_allowed() {
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
        Handler handler = getHandlers("cluster1").get(id);

        assertTrue(handler.getServerBindings().contains(UserBindingPattern.fromHttpPath("/document-api/reserved-for-internal-use/feedapi")));
        assertTrue(handler.getServerBindings().contains(UserBindingPattern.fromHttpPath("/document-api/reserved-for-internal-use/feedapi/")));

        assertEquals(2, handler.getServerBindings().size());
    }

    @Test
    void test_handler_setup() {
        Element elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                "  <document-api />",
                nodesXml,
                "</container>");
        createModel(root, elem);

        Map<String, Handler> handlerMap = getHandlers("cluster1");

        assertNotNull(handlerMap.get("com.yahoo.container.handler.VipStatusHandler"));
        assertNotNull(handlerMap.get("com.yahoo.container.handler.observability.ApplicationStatusHandler"));
        assertNotNull(handlerMap.get("com.yahoo.container.jdisc.state.StateHandler"));

        assertNotNull(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler"));
        assertTrue(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings()
                .contains(SystemBindingPattern.fromHttpPath("/reserved-for-internal-use/feedapi")));
        assertTrue(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings()
                .contains(SystemBindingPattern.fromHttpPath("/reserved-for-internal-use/feedapi")));
        assertEquals(2, handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings().size());
    }

    @Test
    void nonexisting_fields_can_be_ignored() {
        Element elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                "  <document-api>" +
                        "    <ignore-undefined-fields>true</ignore-undefined-fields>" +
                        nodesXml,
                "  </document-api>" +
                        "</container>");
        ContainerModel model = createModel(root, elem).get(0);

        var documentManager = new DocumentmanagerConfig.Builder();
        model.getCluster().getConfig(documentManager);
        assertTrue(documentManager.build().ignoreundefinedfields());
    }

    @Test
    void feeding_api_have_separate_threadpools() {
        Element elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                "  <document-api />",
                nodesXml,
                "</container>");
        root = new MockRoot("root", new MockApplicationPackage.Builder().build());
        createModel(root, elem);
        Map<String, Handler> handlers = getHandlers("cluster1");
        Handler feedApiHandler = handlers.get("com.yahoo.vespa.http.server.FeedHandler");
        Set<String> injectedComponentIds = feedApiHandler.getInjectedComponentIds();
        assertTrue(injectedComponentIds.contains("threadpool@feedapi-handler"));

        ContainerThreadpoolConfig config = root.getConfig(
                ContainerThreadpoolConfig.class, "cluster1/component/com.yahoo.vespa.http.server.FeedHandler/threadpool@feedapi-handler");
        assertEquals(-4, config.maxThreads());
        assertEquals(-4, config.minThreads());
    }

    @Test
    void threadpools_configuration_can_be_overridden() {
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

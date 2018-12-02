// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespaclient.config.FeederConfig;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.11
 */
public class ContainerDocumentApiBuilderTest extends ContainerModelBuilderTestBase {

    private Map<String, Handler<?>> getHandlers(String clusterName) {
        ContainerCluster cluster = (ContainerCluster) root.getChildren().get(clusterName);
        Map<String, Handler<?>> handlerMap = new HashMap<>();
        Collection<Handler<?>> handlers = cluster.getHandlers();
        for (Handler<?> handler : handlers) {
            assertThat(handlerMap.containsKey(handler.getComponentId().toString()), is(false));  //die on overwrites
            handlerMap.put(handler.getComponentId().toString(), handler);
        }
        return handlerMap;
    }

    @Test
    public void document_api_config_is_added_to_container_cluster() throws Exception {
        Element elem = DomBuilderTest.parse(
                "<jdisc id='cluster1' version='1.0'>",
                "  <document-api>",
                "    <abortondocumenterror>false</abortondocumenterror>",
                "    <maxpendingdocs>4321</maxpendingdocs>",
                "    <retrydelay>12.34</retrydelay>",
                "    <route>non-default</route>",
                "  </document-api>",
                nodesXml,
                "</jdisc>");
        createModel(root, elem);
        ContainerCluster cluster = (ContainerCluster)root.getProducer("cluster1");
        FeederConfig.Builder builder = new FeederConfig.Builder();
        cluster.getDocumentApi().getConfig(builder);
        FeederConfig config = new FeederConfig(builder);
        assertThat(config.abortondocumenterror(), is(false));
        assertThat(config.maxpendingdocs(), is(4321));
        assertThat(config.retrydelay(), is(12.34));
        assertThat(config.route(), is("non-default"));
    }

    @Test
    public void custom_bindings_are_allowed() throws Exception {
        Element elem = DomBuilderTest.parse(
                "<jdisc id='cluster1' version='1.0'>",
                "  <document-api>",
                "    <binding>https://*/document-api/</binding>",
                "    <binding>missing-trailing-slash</binding>",
                "  </document-api>",
                nodesXml,
                "</jdisc>");
        createModel(root, elem);

        verifyCustomBindings("com.yahoo.feedhandler.VespaFeedHandler", "feed");
        verifyCustomBindings("com.yahoo.feedhandler.VespaFeedHandlerCompatibility", "document");
        verifyCustomBindings("com.yahoo.feedhandler.VespaFeedHandlerGet", "get");
        verifyCustomBindings("com.yahoo.feedhandler.VespaFeedHandlerRemove", "remove");
        verifyCustomBindings("com.yahoo.feedhandler.VespaFeedHandlerRemoveLocation", "removelocation");
        verifyCustomBindings("com.yahoo.feedhandler.VespaFeedHandlerStatus", "feedstatus");
        verifyCustomBindings("com.yahoo.feedhandler.VespaFeedHandlerVisit", "visit");
        verifyCustomBindings("com.yahoo.vespa.http.server.FeedHandler", ContainerCluster.RESERVED_URI_PREFIX + "/feedapi");
    }

    private void verifyCustomBindings(String id, String bindingSuffix) {
        Handler<?> handler = getHandlers("cluster1").get(id);

        assertThat(handler.getServerBindings(), hasItem("https://*/document-api/" + bindingSuffix));
        assertThat(handler.getServerBindings(), hasItem("https://*/document-api/" + bindingSuffix + "/"));
        assertThat(handler.getServerBindings(), hasItem("missing-trailing-slash/" + bindingSuffix));
        assertThat(handler.getServerBindings(), hasItem("missing-trailing-slash/" + bindingSuffix + "/"));

        assertThat(handler.getServerBindings().size(), is(4));
    }

    @Test
    public void requireThatHandlersAreSetup() throws Exception {
        Element elem = DomBuilderTest.parse(
                "<jdisc id='cluster1' version='1.0'>",
                "  <document-api />",
                nodesXml,
                "</jdisc>");
        createModel(root, elem);

        Map<String, Handler<?>> handlerMap = getHandlers("cluster1");

        assertThat(handlerMap.get("com.yahoo.container.config.StatisticsRequestHandler"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.container.handler.VipStatusHandler"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.container.handler.observability.ApplicationStatusHandler"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.container.jdisc.state.StateHandler"), not(nullValue()));

        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandler"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandler").getServerBindings().contains("http://*/feed"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandler").getServerBindings().contains("https://*/feed"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandler").getServerBindings().contains("http://*/feed/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandler").getServerBindings().contains("https://*/feed/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandler").getServerBindings().size(), equalTo(4));

        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerCompatibility").getComponentId().toString(), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerCompatibility").getServerBindings().contains("http://*/document"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerCompatibility").getServerBindings().contains("https://*/document"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerCompatibility").getServerBindings().contains("http://*/document/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerCompatibility").getServerBindings().contains("https://*/document/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerCompatibility").getServerBindings().size(), equalTo(4));

        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerGet"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerGet").getServerBindings().contains("http://*/get"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerGet").getServerBindings().contains("https://*/get"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerGet").getServerBindings().contains("http://*/get/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerGet").getServerBindings().contains("https://*/get/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerGet").getServerBindings().size(), equalTo(4));

        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerRemove"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerRemove").getServerBindings().contains("http://*/remove"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerRemove").getServerBindings().contains("https://*/remove"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerRemove").getServerBindings().contains("http://*/remove/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerRemove").getServerBindings().contains("https://*/remove/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerRemove").getServerBindings().size(), equalTo(4));

        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerRemoveLocation"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerRemoveLocation").getServerBindings().contains("http://*/removelocation"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerRemoveLocation").getServerBindings().contains("https://*/removelocation"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerRemoveLocation").getServerBindings().contains("http://*/removelocation/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerRemoveLocation").getServerBindings().contains("https://*/removelocation/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerRemoveLocation").getServerBindings().size(), equalTo(4));

        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerStatus"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerStatus").getServerBindings().contains("http://*/feedstatus"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerStatus").getServerBindings().contains("https://*/feedstatus"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerStatus").getServerBindings().contains("http://*/feedstatus/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerStatus").getServerBindings().contains("https://*/feedstatus/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerStatus").getServerBindings().size(), equalTo(4));

        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerVisit"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerVisit").getServerBindings().contains("http://*/visit"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerVisit").getServerBindings().contains("https://*/visit"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerVisit").getServerBindings().contains("http://*/visit/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerVisit").getServerBindings().contains("https://*/visit/"), is(true));
        assertThat(handlerMap.get("com.yahoo.feedhandler.VespaFeedHandlerVisit").getServerBindings().size(), equalTo(4));

        assertThat(handlerMap.get("com.yahoo.search.handler.SearchHandler"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.search.handler.SearchHandler").getServerBindings().contains("http://*/search/*"), is(true));
        assertThat(handlerMap.get("com.yahoo.search.handler.SearchHandler").getServerBindings().contains("https://*/search/*"), is(true));
        assertThat(handlerMap.get("com.yahoo.search.handler.SearchHandler").getServerBindings().size(), equalTo(2));

        assertThat(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler"), not(nullValue()));
        assertThat(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings().contains("http://*/" + ContainerCluster.RESERVED_URI_PREFIX + "/feedapi"), is(true));
        assertThat(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings().contains("https://*/" + ContainerCluster.RESERVED_URI_PREFIX + "/feedapi"), is(true));
        assertThat(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings().contains("http://*/" + ContainerCluster.RESERVED_URI_PREFIX + "/feedapi/"), is(true));
        assertThat(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings().contains("https://*/" + ContainerCluster.RESERVED_URI_PREFIX + "/feedapi/"), is(true));
        assertThat(handlerMap.get("com.yahoo.vespa.http.server.FeedHandler").getServerBindings().size(), equalTo(4));
    }
}

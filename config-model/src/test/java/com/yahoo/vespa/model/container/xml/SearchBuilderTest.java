// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static com.yahoo.test.Matchers.hasItemWithMethod;
import static com.yahoo.vespa.model.container.search.ContainerSearch.QUERY_PROFILE_REGISTRY_CLASS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author gjoranv
 */
public class SearchBuilderTest extends ContainerModelBuilderTestBase {

    private ChainsConfig chainsConfig() {
        return root.getConfig(ChainsConfig.class, "default/component/com.yahoo.search.handler.SearchHandler");
    }

    @Test
    void search_handler_bindings_can_be_overridden() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search>",
                "    <binding>http://*/binding0</binding>",
                "    <binding>http://*/binding1</binding>",
                "  </search>",
                nodesXml,
                "</container>");

        createModel(root, clusterElem);

        String discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default").toString();
        assertTrue(discBindingsConfig.contains(".serverBindings[0] \"http://*/binding0\""));
        assertTrue(discBindingsConfig.contains(".serverBindings[1] \"http://*/binding1\""));
        assertFalse(discBindingsConfig.contains("/search/*"));
    }

    @Test
    void search_handler_bindings_can_be_disabled() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search>",
                "    <binding/>",
                "  </search>",
                nodesXml,
                "</container>");

        createModel(root, clusterElem);

        String discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default").toString();
        assertFalse(discBindingsConfig.contains("/search/*"));
    }

    @Test
    void search_handler_binding_can_be_stolen_by_user_configured_handler() {
        var myHandler = "replaces_search_handler";
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search />",
                "  <handler id='" + myHandler + "'>",
                "    <binding>" + SearchHandler.DEFAULT_BINDING.patternString() + "</binding>",
                "  </handler>",
                nodesXml,
                "</container>");

        createModel(root, clusterElem);

        var discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default");
        assertEquals(SearchHandler.DEFAULT_BINDING.patternString(), discBindingsConfig.handlers(myHandler).serverBindings(0));
        assertNull(discBindingsConfig.handlers(SearchHandler.HANDLER_CLASSNAME));
    }

    @Test
    void empty_search_element_gives_default_chains() {
        createClusterWithOnlyDefaultChains();
        assertThat(chainsConfig().chains(), hasItemWithMethod("vespaPhases", "id"));
        assertThat(chainsConfig().chains(), hasItemWithMethod("native", "id"));
        assertThat(chainsConfig().chains(), hasItemWithMethod("vespa", "id"));
    }

    @Test
    void query_profiles_registry_component_is_added() {
        createClusterWithOnlyDefaultChains();
        ApplicationContainerCluster cluster = (ApplicationContainerCluster) root.getChildren().get("default");
        var queryProfileRegistryId = ComponentId.fromString(QUERY_PROFILE_REGISTRY_CLASS);
        assertTrue(cluster.getComponentsMap().containsKey(queryProfileRegistryId));
    }

    private void createClusterWithOnlyDefaultChains() {
        Element containerElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search/>",
                "  <nodes>",
                "    <node hostalias='mockhost' />",
                "  </nodes>",
                "</container>");

        createModel(root, containerElem);
    }

    @Test
    void manually_setting_up_search_handler_is_forbidden() {
        try {
            Element clusterElem = DomBuilderTest.parse(
                    "<container id='default' version='1.0'>",
                    "  <handler id='com.yahoo.search.handler.SearchHandler' />",
                    nodesXml,
                    " </container>");


            createModel(root, clusterElem);
            fail("Expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Setting up com.yahoo.search.handler.SearchHandler manually is not supported"));
        }
    }

    @Test
    void cluster_is_connected_to_content_clusters() {
        String hosts = hostsXml();

        String services = "" +
                "<services>" +
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='mockhost'/>" +
                "  </admin>" +
                "  <container version='1.0' id='container'>" +
                "      <search>" +
                "        <chain id='mychain' inherits='vespa'/>" +
                "      </search>" +
                "      <nodes>" +
                "        <node hostalias=\"mockhost\" />" +
                "      </nodes>" +
                "  </container>" +
                contentXml() +
                "</services>";

        VespaModel model = getVespaModelWithMusic(hosts, services);

        ApplicationContainerCluster cluster = model.getContainerClusters().get("container");
        assertFalse(cluster.getSearchChains().localProviders().isEmpty());
    }

    @Test
    void cluster_is_connected_to_search_clusters() {
        String hosts = hostsXml();

        String services = "" +
                "<services>" +
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='mockhost'/>" +
                "  </admin>" +
                "  <container version='1.0' id='container'>" +
                "      <search>" +
                "        <chain id='mychain' inherits='vespa'/>" +
                "      </search>" +
                "      <nodes>" +
                "        <node hostalias=\"mockhost\" />" +
                "      </nodes>" +
                "  </container>" +
                contentXml() +
                "</services>";

        VespaModel model = getVespaModelWithMusic(hosts, services);

        ApplicationContainerCluster cluster = model.getContainerClusters().get("container");
        assertFalse(cluster.getSearchChains().localProviders().isEmpty());
    }

    @Test
    void search_handler_has_dedicated_threadpool() {
        createBasicSearchModel();

        Handler searchHandler = getHandler("default", SearchHandler.HANDLER_CLASSNAME);
        assertTrue(searchHandler.getInjectedComponentIds().contains("threadpool@search-handler"));

        ContainerThreadpoolConfig config = root.getConfig(
                ContainerThreadpoolConfig.class, "default/component/" + SearchHandler.HANDLER_CLASSNAME + "/threadpool@search-handler");
        assertEquals(-2, config.maxThreads());
        assertEquals(-2, config.minThreads());
        assertEquals(-40, config.queueSize());
    }

    @Test
    void threadpool_configuration_can_be_overridden() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search>",
                "    <threadpool>",
                "      <max-threads>100</max-threads>",
                "      <min-threads>80</min-threads>",
                "      <queue-size>10</queue-size>",
                "    </threadpool>",
                "  </search>",
                nodesXml,
                "</container>");
        createModel(root, clusterElem);
        ContainerThreadpoolConfig config = root.getConfig(
                ContainerThreadpoolConfig.class, "default/component/" + SearchHandler.HANDLER_CLASSNAME + "/threadpool@search-handler");
        assertEquals(100, config.maxThreads());
        assertEquals(80, config.minThreads());
        assertEquals(10, config.queueSize());
    }

    @Test
    void ExecutionFactory_gets_same_chains_config_as_SearchHandler() {
        createBasicSearchModel();
        Component<?, ?> executionFactory = ((SearchHandler) getComponent("default", SearchHandler.HANDLER_CLASSNAME))
                .getChildren().get(SearchHandler.EXECUTION_FACTORY_CLASSNAME);

        ChainsConfig executionFactoryChainsConfig = root.getConfig(ChainsConfig.class, executionFactory.getConfigId());
        assertEquals(chainsConfig(), executionFactoryChainsConfig);
    }

    private VespaModel getVespaModelWithMusic(String hosts, String services) {
        return new VespaModelCreatorWithMockPkg(hosts, services, ApplicationPackageUtils.generateSchemas("music")).create();
    }

    private void createBasicSearchModel() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search />",
                nodesXml,
                "</container>");

        createModel(root, clusterElem);
    }

    private String hostsXml() {
        return "" +
                    "<hosts>  " +
                    "  <host name=\"node0\">" +
                    "    <alias>mockhost</alias>" +
                    "  </host>" +
                    "</hosts>";
    }

    private String contentXml() {
        return  "  <content version=\"1.0\" id='content'>"+
                "    <documents>\n" +
                "      <document type=\"music\" mode='index'/>\n" +
                "    </documents>\n" +
                "    <redundancy>3</redundancy>"+
                "    <group>"+
                "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>"+
                "    </group>"+
                "  </content>";
    }

}

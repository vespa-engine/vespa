// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.collections.Pair;
import com.yahoo.component.ComponentId;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.QrConfig;
import com.yahoo.container.config.StatisticsRequestHandler;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.core.VipStatusConfig;
import com.yahoo.container.handler.VipStatusHandler;
import com.yahoo.container.handler.observability.ApplicationStatusHandler;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.container.servlet.ServletConfigConfig;
import com.yahoo.container.usability.BindingsOverviewHandler;
import com.yahoo.jdisc.http.ServletPathsConfig;
import com.yahoo.net.HostName;
import com.yahoo.prelude.cluster.QrMonitorConfig;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.SecretStore;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.HttpFilter;
import com.yahoo.vespa.model.content.utils.ContentClusterUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static com.yahoo.test.LinePatternMatcher.containsLineWithPattern;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author gjoranv
 */
public class ContainerModelBuilderTest extends ContainerModelBuilderTestBase {

    @Test
    public void default_port_is_4080() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc version='1.0'>",
                  nodesXml,
                "</jdisc>" );
        createModel(root, clusterElem);
        AbstractService container = (AbstractService)root.getProducer("jdisc/container.0");
        assertThat(container.getRelativePort(0), is(getDefaults().vespaWebServicePort()));
    }

    @Test
    public void http_server_port_is_configurable_and_does_not_affect_other_ports() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc version='1.0'>",
                "  <http>",
                "    <server port='9000' id='foo' />",
                "  </http>",
                  nodesXml,
                "</jdisc>" );
        createModel(root, clusterElem);
        AbstractService container = (AbstractService)root.getProducer("jdisc/container.0");
        assertThat(container.getRelativePort(0), is(9000));
        assertThat(container.getRelativePort(1), is(not(9001)));
    }

    @Test
    public void fail_if_http_port_is_not_4080_in_hosted_vespa() throws Exception {
        String servicesXml =
                "<services>" +
                "<admin version='3.0'>" +
                "    <nodes count='1'/>" +
                "</admin>" +
                "<jdisc version='1.0'>" +
                "  <http>" +
                "    <server port='9000' id='foo' />" +
                "  </http>" +
                nodesXml +
                "</jdisc>" +
                "</services>";
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        // Need to create VespaModel to make deploy properties have effect
        final MyLogger logger = new MyLogger();
        new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .deployLogger(logger)
                .properties(new DeployProperties.Builder()
                        .hostedVespa(true)
                        .build())
                .build(true));
        assertFalse(logger.msgs.isEmpty());
        assertThat(logger.msgs.get(0).getSecond(), containsString(String.format("You cannot set port to anything else than %d", Container.BASEPORT)));
    }

    private class MyLogger implements DeployLogger {
        List<Pair<Level, String>> msgs = new ArrayList<>();
        @Override
        public void log(Level level, String message) {
            msgs.add(new Pair<>(level, message));
        }
    }

    @Test
    public void one_cluster_with_explicit_port_and_one_without_is_ok() throws Exception {
        Element cluster1Elem = DomBuilderTest.parse(
                "<jdisc id='cluster1' version='1.0' />");
        Element cluster2Elem = DomBuilderTest.parse(
                "<jdisc id='cluster2' version='1.0'>",
                "  <http>",
                "    <server port='8000' id='foo' />",
                "  </http>",
                "</jdisc>");
        createModel(root, cluster1Elem, cluster2Elem);
    }

    @Test
    public void two_clusters_without_explicit_port_throws_exception() throws SAXException, IOException {
        Element cluster1Elem = DomBuilderTest.parse(
                "<jdisc id='cluster1' version='1.0'>",
                  nodesXml,
                "</jdisc>" );
        Element cluster2Elem = DomBuilderTest.parse(
                "<jdisc id='cluster2' version='1.0'>",
                  nodesXml,
                "</jdisc>" );
        try {
            createModel(root, cluster1Elem, cluster2Elem);
            fail("Expected exception");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("cannot reserve port"));
        }
    }

    @Test
    public void verify_bindings_for_builtin_handlers() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0' />"
        );
        createModel(root, clusterElem);
        JdiscBindingsConfig config = root.getConfig(JdiscBindingsConfig.class, "default/container.0");

        JdiscBindingsConfig.Handlers defaultRootHandler = config.handlers(BindingsOverviewHandler.class.getName());
        assertThat(defaultRootHandler.serverBindings(), contains("*://*/"));

        JdiscBindingsConfig.Handlers applicationStatusHandler = config.handlers(ApplicationStatusHandler.class.getName());
        assertThat(applicationStatusHandler.serverBindings(),
                   contains("http://*/ApplicationStatus", "https://*/ApplicationStatus"));

        JdiscBindingsConfig.Handlers statisticsRequestHandler = config.handlers(StatisticsRequestHandler.class.getName());
        assertTrue(statisticsRequestHandler.serverBindings(0).startsWith("http://*/statistics"));
        assertTrue(statisticsRequestHandler.serverBindings(1).startsWith("https://*/statistics"));

        JdiscBindingsConfig.Handlers fileRequestHandler = config.handlers(VipStatusHandler.class.getName());
        assertThat(fileRequestHandler.serverBindings(),
                   contains("http://*/status.html", "https://*/status.html"));
    }

    @Test
    public void default_root_handler_is_disabled_when_user_adds_a_handler_with_same_binding() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>" +
                        "  <handler id='userRootHandler'>" +
                        "    <binding>" + ContainerCluster.ROOT_HANDLER_BINDING + "</binding>" +
                        "  </handler>" +
                        "</jdisc>");
        createModel(root, clusterElem);

        ComponentsConfig.Components userRootHandler = getComponent(componentsConfig(), BindingsOverviewHandler.class.getName());
        assertThat(userRootHandler, nullValue());
    }

    @Test
    public void handler_bindings_are_included_in_discBindings_config() throws Exception {
        createClusterWithJDiscHandler();
        String discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default").toString();
        assertThat(discBindingsConfig, containsString("{discHandler}"));
        assertThat(discBindingsConfig, containsString(".serverBindings[0] \"binding0\""));
        assertThat(discBindingsConfig, containsString(".serverBindings[1] \"binding1\""));
        assertThat(discBindingsConfig, containsString(".clientBindings[0] \"clientBinding\""));
    }

    @Test
    public void handlers_are_included_in_components_config() throws Exception {
        createClusterWithJDiscHandler();
        assertThat(componentsConfig().toString(), containsString(".id \"discHandler\""));
    }

    private void createClusterWithJDiscHandler() throws SAXException, IOException {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <handler id='discHandler'>",
                "    <binding>binding0</binding>",
                "    <binding>binding1</binding>",
                "    <clientBinding>clientBinding</clientBinding>",
                "  </handler>",
                "</jdisc>");

        createModel(root, clusterElem);
    }

    @Test
    public void servlets_are_included_in_ServletPathConfig() throws Exception {
        createClusterWithServlet();
        ServletPathsConfig servletPathsConfig = root.getConfig(ServletPathsConfig.class, "default");
        assertThat(servletPathsConfig.servlets().values().iterator().next().path(), is("p/a/t/h"));
    }

    @Test
    public void servletconfig_is_produced() throws Exception {
        createClusterWithServlet();

        String configId = getContainerCluster("default").getServletMap().
                               values().iterator().next().getConfigId();

        ServletConfigConfig servletConfig = root.getConfig(ServletConfigConfig.class, configId);

        assertThat(servletConfig.map().get("myKey"), is("myValue"));
    }

    private void createClusterWithServlet() throws SAXException, IOException {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <servlet id='myServlet' class='myClass' bundle='myBundle'>",
                "    <path>p/a/t/h</path>",
                "    <servlet-config>",
                "      <myKey>myValue</myKey>",
                "    </servlet-config>",
                "  </servlet>",
                "</jdisc>");

        createModel(root, clusterElem);
    }


    @Test
    public void processing_handler_bindings_can_be_overridden() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <processing>",
                "    <binding>binding0</binding>",
                "    <binding>binding1</binding>",
                "  </processing>",
                "</jdisc>");

        createModel(root, clusterElem);

        String discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default").toString();
        assertThat(discBindingsConfig, containsString(".serverBindings[0] \"binding0\""));
        assertThat(discBindingsConfig, containsString(".serverBindings[1] \"binding1\""));
        assertThat(discBindingsConfig, not(containsString("/processing/*")));
    }

    @Test
    public void clientProvider_bindings_are_included_in_discBindings_config() throws Exception {
        createModelWithClientProvider();
        String discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default").toString();
        assertThat(discBindingsConfig, containsString("{discClient}"));
        assertThat(discBindingsConfig, containsString(".clientBindings[0] \"binding0\""));
        assertThat(discBindingsConfig, containsString(".clientBindings[1] \"binding1\""));
        assertThat(discBindingsConfig, containsString(".serverBindings[0] \"serverBinding\""));
    }

    @Test
    public void clientProviders_are_included_in_components_config() throws Exception {
        createModelWithClientProvider();
        assertThat(componentsConfig().toString(), containsString(".id \"discClient\""));
    }

    private void createModelWithClientProvider() throws SAXException, IOException {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>" +
                "  <client id='discClient'>" +
                "    <binding>binding0</binding>" +
                "    <binding>binding1</binding>" +
                "    <serverBinding>serverBinding</serverBinding>" +
                "  </client>" +
                "</jdisc>" );

        createModel(root, clusterElem);
    }

    @Test
    public void serverProviders_are_included_in_components_config() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>" +
                "  <server id='discServer' />" +
                "</jdisc>" );

        createModel(root, clusterElem);

        String componentsConfig = componentsConfig().toString();
        assertThat(componentsConfig, containsString(".id \"discServer\""));
    }

    private String getChainsConfig(String configId) {
        return root.getConfig(ChainsConfig.class, configId).toString();
    }

    @Test
    public void searchHandler_gets_only_search_chains_in_chains_config() throws Exception {
        createClusterWithProcessingAndSearchChains();
        String searchHandlerConfigId = "default/component/com.yahoo.search.handler.SearchHandler";
        String chainsConfig = getChainsConfig(searchHandlerConfigId);
        assertThat(chainsConfig, containsLineWithPattern(".*\\.id \"testSearcher@default\"$"));
        assertThat(chainsConfig, not(containsLineWithPattern(".*\\.id \"testProcessor@default\"$")));
    }

    @Test
    public void processingHandler_gets_only_processing_chains_in_chains_config() throws Exception {
        createClusterWithProcessingAndSearchChains();
        String processingHandlerConfigId = "default/component/com.yahoo.processing.handler.ProcessingHandler";
        String chainsConfig = getChainsConfig(processingHandlerConfigId);
        assertThat(chainsConfig, containsLineWithPattern(".*\\.id \"testProcessor@default\"$"));
        assertThat(chainsConfig, not(containsLineWithPattern(".*\\.id \"testSearcher@default\"$")));
    }

    private void createClusterWithProcessingAndSearchChains() throws SAXException, IOException {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>" +
                        "  <search>" +
                        "    <chain id='default'>" +
                        "      <searcher id='testSearcher' />" +
                        "    </chain>" +
                        "  </search>" +
                        "  <processing>" +
                        "    <chain id='default'>" +
                        "      <processor id='testProcessor'/>" +
                        "    </chain>" +
                        "  </processing>" +
                        nodesXml +
                        " </jdisc>");

        createModel(root, clusterElem);
    }

    @Test
    public void user_config_can_be_overridden_on_node() throws Exception {
        Element containerElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <config name=\"prelude.cluster.qr-monitor\">" +
                "    <requesttimeout>111</requesttimeout>",
                "  </config> " +
                "  <nodes>",
                "    <node hostalias='host1' />",
                "    <node hostalias='host2'>",
                "      <config name=\"prelude.cluster.qr-monitor\">",
                "        <requesttimeout>222</requesttimeout>",
                "      </config> ",
                "    </node>",
                "  </nodes>",
                "</jdisc>");

        root = ContentClusterUtils.createMockRoot(new String[]{"host1", "host2"});
        createModel(root, containerElem);
        ContainerCluster cluster = (ContainerCluster)root.getChildren().get("default");
        assertThat(cluster.getContainers().size(), is(2));
        assertEquals(root.getConfig(QrMonitorConfig.class, "default/container.0").requesttimeout(), 111);
        assertEquals(root.getConfig(QrMonitorConfig.class, "default/container.1").requesttimeout(), 222);
    }

    @Test
    public void legacy_yca_filter_and_its_config_provider_are_included_in_components_config() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <filter id='YcaFilter' /> ",
                "</jdisc>");

        createModel(root, clusterElem);
        assertThat(componentsConfig().toString(), containsString(".id \"YcaFilter\""));

        String providerId = HttpFilter.configProviderId(ComponentId.fromString("YcaFilter")).stringValue();
        assertThat(componentsConfig().toString(), containsString(".id \"" + providerId + "\""));
    }

    @Test
    public void nested_components_are_injected_to_handlers() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <handler id='myHandler'>",
                "    <component id='injected' />",
                "  </handler>",
                "  <client id='myClient'>",  // remember, a client is also a request handler
                "    <component id='injected' />",
                "  </client>",
                "</jdisc>");

        createModel(root, clusterElem);
        Component<?,?> handler = getContainerComponent("default", "myHandler");
        assertThat(handler.getInjectedComponentIds(), hasItem("injected@myHandler"));

        Component<?,?> client = getContainerComponent("default", "myClient");
        assertThat(client.getInjectedComponentIds(), hasItem("injected@myClient"));
    }

    @Test
    public void component_includes_are_added() {
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/application/include_dirs");
        VespaModel model = creator.create(true);
        ContainerCluster cluster = model.getContainerClusters().get("default");
        Map<ComponentId, Component<?, ?>> componentsMap = cluster.getComponentsMap();
        Component<?,?> example = componentsMap.get(
                ComponentId.fromString("test.Exampledocproc"));
        assertThat(example.getComponentId().getName(), is("test.Exampledocproc"));
    }

    @Test
    public void affinity_is_set() throws IOException, SAXException {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <http>",
                "    <server port='" + getDefaults().vespaWebServicePort() + "' id='main' />",
                "  </http>",
                "  <nodes cpu-socket-affinity='true'>",
                "    <node hostalias='node1' />",
                "    <node hostalias='node2'> <server-port id='main' port='5080'/> </node>",
                "    <node hostalias='node3'> <server-port id='main' port='6080'/> </node>",
                "    <node hostalias='node4'> <server-port id='main' port='7080'/> </node>",
                "  </nodes>" +
                "</jdisc>");
        createModel(root, clusterElem);
        assertTrue(getContainerCluster("default").getContainers().get(0).getAffinity().isPresent());
        assertTrue(getContainerCluster("default").getContainers().get(1).getAffinity().isPresent());
        assertTrue(getContainerCluster("default").getContainers().get(2).getAffinity().isPresent());
        assertTrue(getContainerCluster("default").getContainers().get(3).getAffinity().isPresent());

        assertThat(getContainerCluster("default").getContainers().get(0).getAffinity().get().cpuSocket(), is(0));
        assertThat(getContainerCluster("default").getContainers().get(1).getAffinity().get().cpuSocket(), is(1));
        assertThat(getContainerCluster("default").getContainers().get(2).getAffinity().get().cpuSocket(), is(2));
        assertThat(getContainerCluster("default").getContainers().get(3).getAffinity().get().cpuSocket(), is(3));
    }

    @Test
    public void singlenode_servicespec_is_used_with_hosts_xml() throws IOException, SAXException {
        String servicesXml = "<jdisc id='default' version='1.0' />";
        String hostsXml = "<hosts>\n" +
                "    <host name=\"test1.yahoo.com\">\n" +
                "        <alias>node1</alias>\n" +
                "    </host>\n" +
                "</hosts>";
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder()
                .withHosts(hostsXml)
                .withServices(servicesXml)
                .build();
        VespaModel model = new VespaModel(applicationPackage);
        assertThat(model.getHostSystem().getHosts().size(), is(1));
    }

    @Test
    public void http_aliases_are_stored_on_cluster_and_on_service_properties() throws SAXException, IOException {
        Element clusterElem = DomBuilderTest.parse(
                        "<jdisc id='default' version='1.0'>",
                        "  <aliases>",
                        "    <service-alias>service1</service-alias>",
                        "    <service-alias>service2</service-alias>",
                        "    <endpoint-alias>foo1.bar1.com</endpoint-alias>",
                        "    <endpoint-alias>foo2.bar2.com</endpoint-alias>",
                        "  </aliases>",
                        "  <nodes>",
                        "    <node hostalias='host1' />",
                        "  </nodes>",
                        "</jdisc>");

        createModel(root, clusterElem);
        assertEquals(getContainerCluster("default").serviceAliases().get(0), "service1");
        assertEquals(getContainerCluster("default").endpointAliases().get(0), "foo1.bar1.com");
        assertEquals(getContainerCluster("default").serviceAliases().get(1), "service2");
        assertEquals(getContainerCluster("default").endpointAliases().get(1), "foo2.bar2.com");

        assertEquals(getContainerCluster("default").getContainers().get(0).getServicePropertyString("servicealiases"), "service1,service2");
        assertEquals(getContainerCluster("default").getContainers().get(0).getServicePropertyString("endpointaliases"), "foo1.bar1.com,foo2.bar2.com");
    }

    @Test
    public void http_aliases_are_only_honored_in_prod_environment() throws SAXException, IOException {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <aliases>",
                "    <service-alias>service1</service-alias>",
                "    <endpoint-alias>foo1.bar1.com</endpoint-alias>",
                "  </aliases>",
                "  <nodes>",
                "    <node hostalias='host1' />",
                "  </nodes>",
                "</jdisc>");

        DeployState deployState = new DeployState.Builder().zone(new Zone(Environment.dev, RegionName.from("us-east-1"))).build(true);
        createModel(root, deployState, clusterElem);
        assertEquals(0, getContainerCluster("default").serviceAliases().size());
        assertEquals(0, getContainerCluster("default").endpointAliases().size());

        assertNull(getContainerCluster("default").getContainers().get(0).getServicePropertyString("servicealiases"));
        assertNull(getContainerCluster("default").getContainers().get(0).getServicePropertyString("endpointaliases"));
    }

    @Test
    public void singlenode_servicespec_is_used_with_hosted_vespa() throws IOException, SAXException {
        String servicesXml = "<jdisc id='default' version='1.0' />";
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .modelHostProvisioner(new InMemoryProvisioner(true, "host1.yahoo.com", "host2.yahoo.com"))
                .applicationPackage(applicationPackage)
                .properties(new DeployProperties.Builder()
                        .multitenant(true)
                        .hostedVespa(true)
                        .build())
                .build(true));
        assertEquals(1, model.getHostSystem().getHosts().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void renderers_named_JsonRenderer_are_not_allowed() throws IOException, SAXException {
        createModel(root, generateContainerElementWithRenderer("JsonRenderer"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void renderers_named_DefaultRenderer_are_not_allowed() throws IOException, SAXException {
        createModel(root, generateContainerElementWithRenderer("DefaultRenderer"));
    }

    @Test
    public void renderers_named_something_else_are_allowed() throws IOException, SAXException {
        createModel(root, generateContainerElementWithRenderer("my-little-renderer"));
    }

    @Test
    public void vip_status_handler_uses_file_for_hosted_vespa() throws Exception {
        String servicesXml = "<services>" +
                "<jdisc version='1.0'>" +
                nodesXml +
                "</jdisc>" +
                "</services>";

        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .properties(new DeployProperties.Builder()
                        .hostedVespa(true)
                        .build())
                .build(true));

        AbstractConfigProducerRoot modelRoot = model.getRoot();
        VipStatusConfig vipStatusConfig = modelRoot.getConfig(VipStatusConfig.class, "jdisc/component/status.html-status-handler");
        assertTrue(vipStatusConfig.accessdisk());
        assertEquals(ContainerModelBuilder.HOSTED_VESPA_STATUS_FILE, vipStatusConfig.statusfile());
    }

    @Test
    public void qrconfig_is_produced() throws IOException, SAXException {
        String servicesXml =
                "<services>" +
                        "<admin version='3.0'>" +
                        "    <nodes count='1'/>" +
                        "</admin>" +
                        "<jdisc id ='default' version='1.0'>" +
                        "  <nodes>" +
                        "    <node hostalias='node1' />" +
                        "  </nodes>" +
                        "</jdisc>" +
                        "</services>";

        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .properties(new DeployProperties.Builder().build())
                .build(true));

        String hostname = HostName.getLocalhost();  // Using the same way of getting hostname as filedistribution model

        QrConfig config = model.getConfig(QrConfig.class, "default/container.0");
        assertEquals("default.container.0", config.discriminator());
        assertEquals(19102, config.rpc().port());
        assertEquals("vespa/service/default/container.0", config.rpc().slobrokId());
        assertEquals(true, config.rpc().enabled());
        assertEquals("", config.rpc().host());
        assertEquals(false, config.restartOnDeploy());
        assertEquals(false, config.coveragereports());
        assertEquals("filedistribution/" + hostname, config.filedistributor().configid());
    }

    @Test
    public void secret_store_can_be_set_up() throws IOException, SAXException {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc version='1.0'>",
                "  <secret-store>",
                "    <group name='group1' environment='env1'/>",
                "  </secret-store>",
                "</jdisc>");
        createModel(root, clusterElem);
        SecretStore secretStore = getContainerCluster("jdisc").getSecretStore().get();
        assertEquals("group1", secretStore.getGroups().get(0).name);
        assertEquals("env1", secretStore.getGroups().get(0).environment);
    }

    private Element generateContainerElementWithRenderer(String rendererId) {
        return DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <search>",
                String.format("    <renderer id='%s'/>", rendererId),
                "  </search>",
                "</jdisc>");
    }
}

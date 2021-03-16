// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.provision.SingleNodeProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.QrConfig;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.core.VipStatusConfig;
import com.yahoo.container.handler.VipStatusHandler;
import com.yahoo.container.handler.metrics.MetricsV2Handler;
import com.yahoo.container.handler.observability.ApplicationStatusHandler;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.container.jdisc.secretstore.SecretStoreConfig;
import com.yahoo.container.servlet.ServletConfigConfig;
import com.yahoo.container.usability.BindingsOverviewHandler;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServletPathsConfig;
import com.yahoo.net.HostName;
import com.yahoo.path.Path;
import com.yahoo.prelude.cluster.QrMonitorConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.SecretStore;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.content.utils.ContentClusterUtils;
import com.yahoo.vespa.model.test.VespaModelTester;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.test.LinePatternMatcher.containsLineWithPattern;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static com.yahoo.vespa.model.container.ContainerCluster.ROOT_HANDLER_BINDING;
import static com.yahoo.vespa.model.container.ContainerCluster.STATE_HANDLER_BINDING_1;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isEmptyString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for "core functionality" of the container model, e.g. ports, or the 'components' and 'bundles' configs.
 *
 * Before adding a new test to this class, check if the test fits into one of the other existing subclasses
 * of {@link ContainerModelBuilderTestBase}. If not, consider creating a new subclass.
 *
 * @author gjoranv
 */
public class ContainerModelBuilderTest extends ContainerModelBuilderTestBase {
    @Rule
    public TemporaryFolder applicationFolder = new TemporaryFolder();

    @Test
    public void deprecated_jdisc_tag_is_allowed() {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc version='1.0'>",
                nodesXml,
                "</jdisc>" );
        TestLogger logger = new TestLogger();
        createModel(root, logger, clusterElem);
        AbstractService container = (AbstractService)root.getProducer("jdisc/container.0");
        assertNotNull(container);

        assertFalse(logger.msgs.isEmpty());
        assertEquals(Level.WARNING, logger.msgs.get(0).getFirst());
        assertEquals("'jdisc' is deprecated as tag name. Use 'container' instead.", logger.msgs.get(0).getSecond());
    }

    @Test
    public void default_port_is_4080() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                  nodesXml,
                "</container>" );
        createModel(root, clusterElem);
        AbstractService container = (AbstractService)root.getProducer("container/container.0");
        assertThat(container.getRelativePort(0), is(getDefaults().vespaWebServicePort()));
    }

    @Test
    public void http_server_port_is_configurable_and_does_not_affect_other_ports() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <http>",
                "    <server port='9000' id='foo' />",
                "  </http>",
                  nodesXml,
                "</container>" );
        createModel(root, clusterElem);
        AbstractService container = (AbstractService)root.getProducer("container/container.0");
        assertThat(container.getRelativePort(0), is(9000));
        assertThat(container.getRelativePort(1), is(not(9001)));
    }

    @Test
    public void omitting_http_server_port_gives_default() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <http>",
                "    <server id='foo'/>",
                "  </http>",
                nodesXml,
                "</container>" );
        createModel(root, clusterElem);
        AbstractService container = (AbstractService)root.getProducer("container/container.0");
        assertEquals(Defaults.getDefaults().vespaWebServicePort(), container.getRelativePort(0));
    }

    @Test
    public void fail_if_http_port_is_not_default_in_hosted_vespa() throws Exception {
        try {
            String servicesXml =
                    "<services>" +
                    "<admin version='3.0'>" +
                    "    <nodes count='1'/>" +
                    "</admin>" +
                    "<container version='1.0'>" +
                    "  <http>" +
                    "    <server port='9000' id='foo' />" +
                    "  </http>" +
                    nodesXml +
                    "</container>" +
                    "</services>";
            ApplicationPackage applicationPackage = new MockApplicationPackage.Builder().withServices(servicesXml).build();
            // Need to create VespaModel to make deploy properties have effect
            TestLogger logger = new TestLogger();
            new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                                                                  .applicationPackage(applicationPackage)
                                                                  .deployLogger(logger)
                                                                  .properties(new TestProperties().setHostedVespa(true))
                                                                  .build());
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            // Success
            assertEquals("Illegal port 9000 in http server 'foo': Port must be set to " + Defaults.getDefaults().vespaWebServicePort(),
                         e.getMessage());
        }
    }

    @Test
    public void one_cluster_with_explicit_port_and_one_without_is_ok() {
        Element cluster1Elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0' />");
        Element cluster2Elem = DomBuilderTest.parse(
                "<container id='cluster2' version='1.0'>",
                "  <http>",
                "    <server port='8000' id='foo' />",
                "  </http>",
                "</container>");
        createModel(root, cluster1Elem, cluster2Elem);
    }

    @Test
    public void two_clusters_without_explicit_port_throws_exception() {
        Element cluster1Elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                  nodesXml,
                "</container>" );
        Element cluster2Elem = DomBuilderTest.parse(
                "<container id='cluster2' version='1.0'>",
                  nodesXml,
                "</container>" );
        try {
            createModel(root, cluster1Elem, cluster2Elem);
            fail("Expected exception");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("cannot reserve port"));
        }
    }

    @Test
    public void verify_bindings_for_builtin_handlers() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0' />"
        );
        createModel(root, clusterElem);
        JdiscBindingsConfig config = root.getConfig(JdiscBindingsConfig.class, "default/container.0");

        JdiscBindingsConfig.Handlers defaultRootHandler = config.handlers(BindingsOverviewHandler.class.getName());
        assertThat(defaultRootHandler.serverBindings(), contains("http://*/"));

        JdiscBindingsConfig.Handlers applicationStatusHandler = config.handlers(ApplicationStatusHandler.class.getName());
        assertThat(applicationStatusHandler.serverBindings(), contains("http://*/ApplicationStatus"));

        JdiscBindingsConfig.Handlers fileRequestHandler = config.handlers(VipStatusHandler.class.getName());
        assertThat(fileRequestHandler.serverBindings(), contains("http://*/status.html"));

        JdiscBindingsConfig.Handlers metricsV2Handler = config.handlers(MetricsV2Handler.class.getName());
        assertThat(metricsV2Handler.serverBindings(), contains("http://*/metrics/v2", "http://*/metrics/v2/*"));
    }

    @Test
    public void default_root_handler_binding_can_be_stolen_by_user_configured_handler() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>" +
                        "  <handler id='userRootHandler'>" +
                        "    <binding>" + ROOT_HANDLER_BINDING.patternString() + "</binding>" +
                        "  </handler>" +
                        "</container>");
        createModel(root, clusterElem);

        // The handler is still set up.
        ComponentsConfig.Components userRootHandler = getComponent(componentsConfig(), BindingsOverviewHandler.class.getName());
        assertThat(userRootHandler, notNullValue());

        // .. but it has no bindings
        var discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default");
        assertThat(discBindingsConfig.handlers(BindingsOverviewHandler.class.getName()), is(nullValue()));
    }

    @Test
    public void reserved_binding_cannot_be_stolen_by_user_configured_handler() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>" +
                        "  <handler id='userHandler'>" +
                        "    <binding>" + STATE_HANDLER_BINDING_1.patternString() + "</binding>" +
                        "  </handler>" +
                        "</container>");
        try {
            createModel(root, clusterElem);
            fail("Expected exception when stealing a reserved binding.");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Binding 'http://*/state/v1' is a reserved Vespa binding " +
                                                  "and cannot be used by handler: userHandler"));
        }
    }

    @Test
    public void handler_bindings_are_included_in_discBindings_config() {
        createClusterWithJDiscHandler();
        String discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default").toString();
        assertThat(discBindingsConfig, containsString("{discHandler}"));
        assertThat(discBindingsConfig, containsString(".serverBindings[0] \"http://*/binding0\""));
        assertThat(discBindingsConfig, containsString(".serverBindings[1] \"http://*/binding1\""));
        assertThat(discBindingsConfig, containsString(".clientBindings[0] \"http://*/clientBinding\""));
    }

    @Test
    public void handlers_are_included_in_components_config() {
        createClusterWithJDiscHandler();
        assertThat(componentsConfig().toString(), containsString(".id \"discHandler\""));
    }

    private void createClusterWithJDiscHandler() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <handler id='discHandler'>",
                "    <binding>http://*/binding0</binding>",
                "    <binding>http://*/binding1</binding>",
                "    <clientBinding>http://*/clientBinding</clientBinding>",
                "  </handler>",
                "</container>");

        createModel(root, clusterElem);
    }

    @Test
    public void servlets_are_included_in_ServletPathConfig() {
        createClusterWithServlet();
        ServletPathsConfig servletPathsConfig = root.getConfig(ServletPathsConfig.class, "default");
        assertThat(servletPathsConfig.servlets().values().iterator().next().path(), is("p/a/t/h"));
    }

    @Test
    public void servletconfig_is_produced() {
        createClusterWithServlet();

        String configId = getContainerCluster("default").getServletMap().
                               values().iterator().next().getConfigId();

        ServletConfigConfig servletConfig = root.getConfig(ServletConfigConfig.class, configId);

        assertThat(servletConfig.map().get("myKey"), is("myValue"));
    }

    private void createClusterWithServlet() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <servlet id='myServlet' class='myClass' bundle='myBundle'>",
                "    <path>p/a/t/h</path>",
                "    <servlet-config>",
                "      <myKey>myValue</myKey>",
                "    </servlet-config>",
                "  </servlet>",
                "</container>");

        createModel(root, clusterElem);
    }


    @Test
    public void processing_handler_bindings_can_be_overridden() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <processing>",
                "    <binding>http://*/binding0</binding>",
                "    <binding>http://*/binding1</binding>",
                "  </processing>",
                "</container>");

        createModel(root, clusterElem);

        String discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default").toString();
        assertThat(discBindingsConfig, containsString(".serverBindings[0] \"http://*/binding0\""));
        assertThat(discBindingsConfig, containsString(".serverBindings[1] \"http://*/binding1\""));
        assertThat(discBindingsConfig, not(containsString("/processing/*")));
    }

    @Test
    public void clientProvider_bindings_are_included_in_discBindings_config() {
        createModelWithClientProvider();
        String discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default").toString();
        assertThat(discBindingsConfig, containsString("{discClient}"));
        assertThat(discBindingsConfig, containsString(".clientBindings[0] \"http://*/binding0\""));
        assertThat(discBindingsConfig, containsString(".clientBindings[1] \"http://*/binding1\""));
        assertThat(discBindingsConfig, containsString(".serverBindings[0] \"http://*/serverBinding\""));
    }

    @Test
    public void clientProviders_are_included_in_components_config() {
        createModelWithClientProvider();
        assertThat(componentsConfig().toString(), containsString(".id \"discClient\""));
    }

    private void createModelWithClientProvider() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>" +
                "  <client id='discClient'>" +
                "    <binding>http://*/binding0</binding>" +
                "    <binding>http://*/binding1</binding>" +
                "    <serverBinding>http://*/serverBinding</serverBinding>" +
                "  </client>" +
                "</container>" );

        createModel(root, clusterElem);
    }

    @Test
    public void serverProviders_are_included_in_components_config() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>" +
                "  <server id='discServer' />" +
                "</container>" );

        createModel(root, clusterElem);

        String componentsConfig = componentsConfig().toString();
        assertThat(componentsConfig, containsString(".id \"discServer\""));
    }

    private String getChainsConfig(String configId) {
        return root.getConfig(ChainsConfig.class, configId).toString();
    }

    @Test
    public void searchHandler_gets_only_search_chains_in_chains_config()  {
        createClusterWithProcessingAndSearchChains();
        String searchHandlerConfigId = "default/component/com.yahoo.search.handler.SearchHandler";
        String chainsConfig = getChainsConfig(searchHandlerConfigId);
        assertThat(chainsConfig, containsLineWithPattern(".*\\.id \"testSearcher@default\"$"));
        assertThat(chainsConfig, not(containsLineWithPattern(".*\\.id \"testProcessor@default\"$")));
    }

    @Test
    public void processingHandler_gets_only_processing_chains_in_chains_config()  {
        createClusterWithProcessingAndSearchChains();
        String processingHandlerConfigId = "default/component/com.yahoo.processing.handler.ProcessingHandler";
        String chainsConfig = getChainsConfig(processingHandlerConfigId);
        assertThat(chainsConfig, containsLineWithPattern(".*\\.id \"testProcessor@default\"$"));
        assertThat(chainsConfig, not(containsLineWithPattern(".*\\.id \"testSearcher@default\"$")));
    }

    private void createClusterWithProcessingAndSearchChains() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>" +
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
                        " </container>");

        createModel(root, clusterElem);
    }

    @Test
    public void user_config_can_be_overridden_on_node() {
        Element containerElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
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
                "</container>");

        root = ContentClusterUtils.createMockRoot(new String[]{"host1", "host2"});
        createModel(root, containerElem);
        ContainerCluster cluster = (ContainerCluster)root.getChildren().get("default");
        assertThat(cluster.getContainers().size(), is(2));
        assertEquals(root.getConfig(QrMonitorConfig.class, "default/container.0").requesttimeout(), 111);
        assertEquals(root.getConfig(QrMonitorConfig.class, "default/container.1").requesttimeout(), 222);
    }

    @Test
    public void nested_components_are_injected_to_handlers() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <handler id='myHandler'>",
                "    <component id='injected' />",
                "  </handler>",
                "  <client id='myClient'>",  // remember, a client is also a request handler
                "    <component id='injected' />",
                "  </client>",
                "</container>");

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
    public void affinity_is_set() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <http>",
                "    <server port='" + getDefaults().vespaWebServicePort() + "' id='main' />",
                "  </http>",
                "  <nodes cpu-socket-affinity='true'>",
                "    <node hostalias='node1' />",
                "  </nodes>" +
                "</container>");
        createModel(root, clusterElem);
        assertTrue(getContainerCluster("default").getContainers().get(0).getAffinity().isPresent());
        assertThat(getContainerCluster("default").getContainers().get(0).getAffinity().get().cpuSocket(), is(0));
    }

    @Test
    public void singlenode_servicespec_is_used_with_hosts_xml() throws IOException, SAXException {
        String servicesXml = "<container id='default' version='1.0' />";
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
        assertThat(model.hostSystem().getHosts().size(), is(1));
    }

    @Test
    public void http_aliases_are_stored_on_cluster_and_on_service_properties() {
        Element clusterElem = DomBuilderTest.parse(
                        "<container id='default' version='1.0'>",
                        "  <aliases>",
                        "    <service-alias>service1</service-alias>",
                        "    <service-alias>service2</service-alias>",
                        "    <endpoint-alias>foo1.bar1.com</endpoint-alias>",
                        "    <endpoint-alias>foo2.bar2.com</endpoint-alias>",
                        "  </aliases>",
                        "  <nodes>",
                        "    <node hostalias='host1' />",
                        "  </nodes>",
                        "</container>");

        createModel(root, clusterElem);
        assertEquals(getContainerCluster("default").serviceAliases().get(0), "service1");
        assertEquals(getContainerCluster("default").endpointAliases().get(0), "foo1.bar1.com");
        assertEquals(getContainerCluster("default").serviceAliases().get(1), "service2");
        assertEquals(getContainerCluster("default").endpointAliases().get(1), "foo2.bar2.com");

        assertEquals(getContainerCluster("default").getContainers().get(0).getServicePropertyString("servicealiases"), "service1,service2");
        assertEquals(getContainerCluster("default").getContainers().get(0).getServicePropertyString("endpointaliases"), "foo1.bar1.com,foo2.bar2.com");
    }

    @Test
    public void http_aliases_are_only_honored_in_prod_environment() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <aliases>",
                "    <service-alias>service1</service-alias>",
                "    <endpoint-alias>foo1.bar1.com</endpoint-alias>",
                "  </aliases>",
                "  <nodes>",
                "    <node hostalias='host1' />",
                "  </nodes>",
                "</container>");

        DeployState deployState = new DeployState.Builder().zone(new Zone(Environment.dev, RegionName.from("us-east-1"))).build();
        createModel(root, deployState, null, clusterElem);
        assertEquals(0, getContainerCluster("default").serviceAliases().size());
        assertEquals(0, getContainerCluster("default").endpointAliases().size());

        assertNull(getContainerCluster("default").getContainers().get(0).getServicePropertyString("servicealiases"));
        assertNull(getContainerCluster("default").getContainers().get(0).getServicePropertyString("endpointaliases"));
    }

    @Test
    public void endpoints_are_added_to_containers() throws IOException, SAXException {
        final var servicesXml = joinLines("",
                "<container id='comics-search' version='1.0'>",
                "  <nodes>",
                "    <node hostalias='host1' />",
                "  </nodes>",
                "</container>"
        );

        final var deploymentXml = joinLines("",
                "<deployment version='1.0'>",
                "  <prod />",
                "</deployment>"
        );

        final var applicationPackage = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withDeploymentSpec(deploymentXml)
                .build();

        final var deployState = new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .zone(new Zone(Environment.prod, RegionName.from("us-east-1")))
                .endpoints(Set.of(new ContainerEndpoint("comics-search", List.of("nalle", "balle"))))
                .properties(new TestProperties().setHostedVespa(true))
                .build();

        final var model = new VespaModel(new NullConfigModelRegistry(), deployState);
        final var containers = model.getContainerClusters().values().stream()
                .flatMap(cluster -> cluster.getContainers().stream())
                .collect(Collectors.toList());

        assertFalse("Missing container objects based on configuration", containers.isEmpty());

        containers.forEach(container -> {
            final var rotations = container.getServicePropertyString("rotations").split(",");
            final var rotationsSet = Set.of(rotations);
            assertEquals(Set.of("balle", "nalle"), rotationsSet);
        });
    }

    @Test
    public void singlenode_servicespec_is_used_with_hosted_vespa() throws IOException, SAXException {
        String servicesXml = "<container id='default' version='1.0' />";
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .modelHostProvisioner(new InMemoryProvisioner(true, false, "host1.yahoo.com", "host2.yahoo.com"))
                .applicationPackage(applicationPackage)
                .properties(new TestProperties()
                        .setMultitenant(true)
                        .setHostedVespa(true))
                .build());
        assertEquals(2, model.hostSystem().getHosts().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void renderers_named_JsonRenderer_are_not_allowed() {
        createModel(root, generateContainerElementWithRenderer("JsonRenderer"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void renderers_named_DefaultRenderer_are_not_allowed() {
        createModel(root, generateContainerElementWithRenderer("XmlRenderer"));
    }

    @Test
    public void renderers_named_something_else_are_allowed() {
        createModel(root, generateContainerElementWithRenderer("my-little-renderer"));
    }

    @Test
    public void vip_status_handler_uses_file_for_hosted_vespa() throws Exception {
        String servicesXml = "<services>" +
                "<container version='1.0'>" +
                nodesXml +
                "</container>" +
                "</services>";

        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .properties(new TestProperties().setHostedVespa(true))
                .build());

        AbstractConfigProducerRoot modelRoot = model.getRoot();
        VipStatusConfig vipStatusConfig = modelRoot.getConfig(VipStatusConfig.class, "container/component/status.html-status-handler");
        assertTrue(vipStatusConfig.accessdisk());
        assertEquals(ContainerModelBuilder.HOSTED_VESPA_STATUS_FILE, vipStatusConfig.statusfile());
    }

    @Test
    public void qrconfig_is_produced() throws IOException, SAXException {
        String servicesXml =
                "<services>" +
                        "<admin version='3.0'>" +
                        "    <nodes count='2'/>" +
                        "</admin>" +
                        "<container id ='default' version='1.0'>" +
                        "  <nodes>" +
                        "    <node hostalias='node1' />" +
                        "  </nodes>" +
                        "</container>" +
                        "</services>";

        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .properties(new TestProperties())
                .build());

        String hostname = HostName.getLocalhost();  // Using the same way of getting hostname as filedistribution model

        QrConfig config = model.getConfig(QrConfig.class, "default/container.0");
        assertEquals("default.container.0", config.discriminator());
        assertEquals(19102, config.rpc().port());
        assertEquals("vespa/service/default/container.0", config.rpc().slobrokId());
        assertTrue(config.rpc().enabled());
        assertEquals("", config.rpc().host());
        assertFalse(config.restartOnDeploy());
        assertEquals("filedistribution/" + hostname, config.filedistributor().configid());
    }

    @Test
    public void secret_store_can_be_set_up() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <secret-store type='oath-ckms'>",
                "    <group name='group1' environment='env1'/>",
                "  </secret-store>",
                "</container>");
        createModel(root, clusterElem);
        SecretStore secretStore = getContainerCluster("container").getSecretStore().get();
        assertEquals("group1", secretStore.getGroups().get(0).name);
        assertEquals("env1", secretStore.getGroups().get(0).environment);
    }

    @Test
    public void cloud_secret_store_requires_configured_secret_store() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <secret-store type='cloud'>",
                "    <aws-parameter-store name='store1' region='eu-north-1'/>",
                "  </secret-store>",
                "</container>");
        try {
            createModel(root, clusterElem);
            fail("secret store not defined");
        } catch (RuntimeException e) {
            assertEquals("No configured secret store named store1", e.getMessage());
        }
    }


    @Test
    public void cloud_secret_store_can_be_set_up() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <secret-store type='cloud'>",
                "    <aws-parameter-store name='store1' region='eu-north-1'/>",
                "  </secret-store>",
                "</container>");

        DeployState state = new DeployState.Builder()
                .properties(
                        new TestProperties()
                                .setHostedVespa(true)
                                .setTenantSecretStores(List.of(new TenantSecretStore("store1", "1234", "role", Optional.of("externalid")))))
                .zone(new Zone(SystemName.Public, Environment.prod, RegionName.defaultName()))
                .build();
        createModel(root, state, null, clusterElem);

        ApplicationContainerCluster container = getContainerCluster("container");
        assertComponentConfigured(container, "com.yahoo.jdisc.cloud.aws.AwsParameterStore");
        CloudSecretStore secretStore = (CloudSecretStore) container.getComponentsMap().get(ComponentId.fromString("com.yahoo.jdisc.cloud.aws.AwsParameterStore"));


        SecretStoreConfig.Builder configBuilder = new SecretStoreConfig.Builder();
        secretStore.getConfig(configBuilder);
        SecretStoreConfig secretStoreConfig = configBuilder.build();

        assertEquals(1, secretStoreConfig.awsParameterStores().size());
        assertEquals("store1", secretStoreConfig.awsParameterStores().get(0).name());
    }

    @Test
    public void missing_security_clients_pem_fails_in_public() {
        Element clusterElem = DomBuilderTest.parse("<container version='1.0' />");

        try {
            DeployState state = new DeployState.Builder()
                    .properties(
                        new TestProperties()
                                .setHostedVespa(true)
                                .setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY"))))
                    .zone(new Zone(SystemName.Public, Environment.prod, RegionName.defaultName()))
                    .build();
            createModel(root, state, null, clusterElem);
        } catch (RuntimeException e) {
            assertEquals("Client certificate authority security/clients.pem is missing - see: https://cloud.vespa.ai/en/security-model#data-plane",
                         e.getMessage());
            return;
        }
        fail();
    }

    @Test
    public void security_clients_pem_is_picked_up() {
        var applicationPackage = new MockApplicationPackage.Builder()
                .withRoot(applicationFolder.getRoot())
                .build();

        applicationPackage.getFile(Path.fromString("security")).createDirectory();
        applicationPackage.getFile(Path.fromString("security/clients.pem")).writeFile(new StringReader("I am a very nice certificate"));

        var deployState = DeployState.createTestState(applicationPackage);

        Element clusterElem = DomBuilderTest.parse("<container version='1.0' />");

        createModel(root, deployState, null, clusterElem);
        assertEquals(Optional.of("I am a very nice certificate"), getContainerCluster("container").getTlsClientAuthority());
    }

    @Test
    public void environment_vars_are_honoured() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <nodes>",
                "    <environment-variables>",
                "      <KMP_SETTING>1</KMP_SETTING>",
                "      <KMP_AFFINITY>granularity=fine,verbose,compact,1,0</KMP_AFFINITY>",
                "    </environment-variables>",
                "    <node hostalias='mockhost'/>",
                "  </nodes>",
                "</container>" );
        createModel(root, clusterElem);
        QrStartConfig.Builder qrStartBuilder = new QrStartConfig.Builder();
        root.getConfig(qrStartBuilder, "container/container.0");
        QrStartConfig qrStartConfig = new QrStartConfig(qrStartBuilder);
        assertEquals("KMP_SETTING=1 KMP_AFFINITY=granularity=fine,verbose,compact,1,0 ", qrStartConfig.qrs().env());
    }

    private void verifyAvailableprocessors(boolean isHosted, Flavor flavor, int expectProcessors) {
        DeployState deployState = new DeployState.Builder()
                .modelHostProvisioner(flavor != null ? new SingleNodeProvisioner(flavor) : new SingleNodeProvisioner())
                .properties(new TestProperties()
                        .setMultitenant(isHosted)
                        .setHostedVespa(isHosted))
                .build();
        MockRoot myRoot = new MockRoot("root", deployState);
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <nodes>",
                "    <node hostalias='localhost'/>",
                "  </nodes>",
                "</container>"
        );

        createModel(myRoot, clusterElem);
        QrStartConfig.Builder qsB = new QrStartConfig.Builder();
        myRoot.getConfig(qsB, "container/container.0");
        QrStartConfig qsC= new QrStartConfig(qsB);
        assertEquals(expectProcessors, qsC.jvm().availableProcessors());
    }

    @Test
    public void requireThatAvailableProcessorsFollowFlavor() {
        verifyAvailableprocessors(false, null,0);
        verifyAvailableprocessors(true, null,0);
        verifyAvailableprocessors(true, new Flavor(new FlavorsConfig.Flavor.Builder().name("test-flavor").minCpuCores(9).build()), 9);
        verifyAvailableprocessors(true, new Flavor(new FlavorsConfig.Flavor.Builder().name("test-flavor").minCpuCores(1).build()), 2);
    }

    @Test
    public void requireThatProvidingEndpointCertificateSecretsOpensPort4443() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                nodesXml,
                "</container>" );

        DeployState state = new DeployState.Builder().properties(new TestProperties().setHostedVespa(true).setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY")))).build();
        createModel(root, state, null, clusterElem);
        ApplicationContainer container = (ApplicationContainer)root.getProducer("container/container.0");

        // Verify that there are two connectors
        List<ConnectorFactory> connectorFactories = container.getHttp().getHttpServer().get().getConnectorFactories();
        assertEquals(2, connectorFactories.size());
        List<Integer> ports = connectorFactories.stream()
                .map(ConnectorFactory::getListenPort)
                .collect(Collectors.toList());
        assertThat(ports, Matchers.containsInAnyOrder(8080, 4443));

        ConnectorFactory tlsPort = connectorFactories.stream().filter(connectorFactory -> connectorFactory.getListenPort() == 4443).findFirst().orElseThrow();

        ConnectorConfig.Builder builder = new ConnectorConfig.Builder();
        tlsPort.getConfig(builder);


        ConnectorConfig connectorConfig = new ConnectorConfig(builder);
        assertTrue(connectorConfig.ssl().enabled());
        assertEquals(ConnectorConfig.Ssl.ClientAuth.Enum.WANT_AUTH, connectorConfig.ssl().clientAuth());
        assertEquals("CERT", connectorConfig.ssl().certificate());
        assertEquals("KEY", connectorConfig.ssl().privateKey());
        assertEquals(4443, connectorConfig.listenPort());

        assertThat("Connector must use Athenz truststore in a non-public system.",
                   connectorConfig.ssl().caCertificateFile(), equalTo("/opt/yahoo/share/ssl/certs/athenz_certificate_bundle.pem"));
        assertThat(connectorConfig.ssl().caCertificate(), isEmptyString());
    }

    @Test
    public void requireThatClientAuthenticationIsEnforced() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                nodesXml,
                "   <http><filtering>" +
                "      <access-control domain=\"vespa\" tls-handshake-client-auth=\"need\"/>" +
                "   </filtering></http>" +
                "</container>" );

        DeployState state = new DeployState.Builder().properties(
                new TestProperties()
                        .setHostedVespa(true)
                        .setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY")))
                        .useAccessControlTlsHandshakeClientAuth(true))
                .build();
        createModel(root, state, null, clusterElem);
        ApplicationContainer container = (ApplicationContainer)root.getProducer("container/container.0");

        List<ConnectorFactory> connectorFactories = container.getHttp().getHttpServer().get().getConnectorFactories();
        ConnectorFactory tlsPort = connectorFactories.stream().filter(connectorFactory -> connectorFactory.getListenPort() == 4443).findFirst().orElseThrow();

        ConnectorConfig.Builder builder = new ConnectorConfig.Builder();
        tlsPort.getConfig(builder);

        ConnectorConfig connectorConfig = new ConnectorConfig(builder);
        assertTrue(connectorConfig.ssl().enabled());
        assertEquals(ConnectorConfig.Ssl.ClientAuth.Enum.NEED_AUTH, connectorConfig.ssl().clientAuth());
        assertEquals("CERT", connectorConfig.ssl().certificate());
        assertEquals("KEY", connectorConfig.ssl().privateKey());
        assertEquals(4443, connectorConfig.listenPort());

        assertThat("Connector must use Athenz truststore in a non-public system.",
                connectorConfig.ssl().caCertificateFile(), equalTo("/opt/yahoo/share/ssl/certs/athenz_certificate_bundle.pem"));
        assertThat(connectorConfig.ssl().caCertificate(), isEmptyString());
    }

    @Test
    public void cluster_with_zookeeper() {
        Function<Integer, String> servicesXml = (nodeCount) -> "<container version='1.0' id='default'>" +
                                                               "<nodes count='" + nodeCount + "'/>" +
                                                               "<zookeeper/>" +
                                                               "</container>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(3);
        {
            VespaModel model = tester.createModel(servicesXml.apply(3), true);
            ApplicationContainerCluster cluster = model.getContainerClusters().get("default");
            assertNotNull(cluster);
            assertComponentConfigured(cluster,"com.yahoo.vespa.curator.Curator");
            cluster.getContainers().forEach(container -> {
                assertComponentConfigured(container, "com.yahoo.vespa.zookeeper.ReconfigurableVespaZooKeeperServer");
                assertComponentConfigured(container, "com.yahoo.vespa.zookeeper.Reconfigurer");
                assertComponentConfigured(container, "com.yahoo.vespa.zookeeper.VespaZooKeeperAdminImpl");

                ZookeeperServerConfig config = model.getConfig(ZookeeperServerConfig.class, container.getConfigId());
                assertEquals(container.index(), config.myid());
                assertEquals(3, config.server().size());
            });
        }
        {
            try {
                tester.createModel(servicesXml.apply(2), true);
                fail("Expected exception");
            } catch (IllegalArgumentException ignored) {}
        }
        {
            String xmlWithNodes =
                    "<?xml version='1.0' encoding='utf-8' ?>" +
                    "<services>" +
                    "  <container version='1.0' id='container1'>" +
                    "     <zookeeper/>" +
                    "     <nodes of='content1'/>" +
                    "  </container>" +
                    "  <content version='1.0' id='content1'>" +
                    "     <nodes count='3'/>" +
                    "   </content>" +
                    "</services>";
            try {
                tester.createModel(xmlWithNodes, true);
                fail("Expected exception");
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void assertComponentConfigured(ApplicationContainerCluster cluster, String componentId) {
        Component<?, ?> component = cluster.getComponentsMap().get(ComponentId.fromString(componentId));
        assertNotNull(component);
    }

    private void assertComponentConfigured(ApplicationContainer container, String id) {
        assertTrue(container.getComponents().getComponents().stream().anyMatch(component -> id.equals(component.getComponentId().getName())));
    }

    private Element generateContainerElementWithRenderer(String rendererId) {
        return DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search>",
                String.format("    <renderer id='%s'/>", rendererId),
                "  </search>",
                "</container>");
    }

}

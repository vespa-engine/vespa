// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.provision.SingleNodeProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.QrConfig;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.core.VipStatusConfig;
import com.yahoo.container.di.config.PlatformBundlesConfig;
import com.yahoo.container.handler.VipStatusHandler;
import com.yahoo.container.handler.metrics.MetricsV2Handler;
import com.yahoo.container.handler.observability.ApplicationStatusHandler;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.container.usability.BindingsOverviewHandler;
import com.yahoo.net.HostName;
import com.yahoo.prelude.cluster.QrMonitorConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModelEvaluation;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.content.utils.ContentClusterUtils;
import com.yahoo.vespa.model.test.VespaModelTester;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.test.LinePatternMatcher.containsLineWithPattern;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static com.yahoo.vespa.model.container.component.chain.ProcessingHandler.PROCESSING_HANDLER_CLASS;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for "core functionality" of the container model, e.g. ports, or the 'components' and 'bundles' configs.
 *
 * Before adding a new test to this class, check if the test fits into one of the other existing subclasses
 * of {@link ContainerModelBuilderTestBase}. If not, consider creating a new subclass.
 *
 * @author gjoranv
 */
public class ContainerModelBuilderTest extends ContainerModelBuilderTestBase {

    @Test
    void model_evaluation_bundles_are_deployed() {
        createBasicContainerModel();
        PlatformBundlesConfig config = root.getConfig(PlatformBundlesConfig.class, "default");
        assertTrue(config.bundlePaths().contains(ContainerModelEvaluation.MODEL_EVALUATION_BUNDLE_FILE.toString()));
        assertTrue(config.bundlePaths().contains(ContainerModelEvaluation.MODEL_INTEGRATION_BUNDLE_FILE.toString()));
    }

    @Test
    void default_port_is_4080() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                nodesXml,
                "</container>");
        createModel(root, clusterElem);
        AbstractService container = (AbstractService) root.getProducer("container/container.0");
        assertEquals(getDefaults().vespaWebServicePort(), container.getRelativePort(0));
    }

    @Test
    void http_server_port_is_configurable_and_does_not_affect_other_ports() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <http>",
                "    <server port='9000' id='foo' />",
                "  </http>",
                nodesXml,
                "</container>");
        createModel(root, clusterElem);
        AbstractService container = (AbstractService) root.getProducer("container/container.0");
        assertEquals(9000, container.getRelativePort(0));
        assertNotEquals(9001, container.getRelativePort(1));
    }

    @Test
    void omitting_http_server_port_gives_default() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <http>",
                "    <server id='foo'/>",
                "  </http>",
                nodesXml,
                "</container>");
        createModel(root, clusterElem);
        AbstractService container = (AbstractService) root.getProducer("container/container.0");
        assertEquals(getDefaults().vespaWebServicePort(), container.getRelativePort(0));
    }

    @Test
    void fail_if_http_port_is_not_default_in_hosted_vespa() throws Exception {
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
            assertEquals("Illegal port 9000 in http server 'foo': Port must be set to " + getDefaults().vespaWebServicePort(),
                    e.getMessage());
        }
    }

    @Test
    void one_cluster_with_explicit_port_and_one_without_is_ok() {
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
    void two_clusters_without_explicit_port_throws_exception() {
        Element cluster1Elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                nodesXml,
                "</container>");
        Element cluster2Elem = DomBuilderTest.parse(
                "<container id='cluster2' version='1.0'>",
                nodesXml,
                "</container>");
        try {
            createModel(root, cluster1Elem, cluster2Elem);
            fail("Expected exception");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("cannot reserve port"));
        }
    }

    @Test
    void builtin_handlers_get_default_threadpool() {
        createBasicContainerModel();

        Handler h1 = getHandler("default", ApplicationStatusHandler.class.getName());
        assertTrue(h1.getInjectedComponentIds().contains("threadpool@default-handler-common"));

        Handler h2 = getHandler("default", BindingsOverviewHandler.class.getName());
        assertTrue(h2.getInjectedComponentIds().contains("threadpool@default-handler-common"));
    }

    @Test
    void verify_bindings_for_builtin_handlers() {
        createBasicContainerModel();
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
    void processing_handler_bindings_can_be_overridden() {
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
    void serverProviders_are_included_in_components_config() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>" +
                        "  <server id='discServer' />" +
                        "</container>");

        createModel(root, clusterElem);

        String componentsConfig = componentsConfig().toString();
        assertThat(componentsConfig, containsString(".id \"discServer\""));
    }

    private String getChainsConfig(String configId) {
        return root.getConfig(ChainsConfig.class, configId).toString();
    }

    @Test
    void searchHandler_gets_only_search_chains_in_chains_config()  {
        createClusterWithProcessingAndSearchChains();
        String searchHandlerConfigId = "default/component/com.yahoo.search.handler.SearchHandler";
        String chainsConfig = getChainsConfig(searchHandlerConfigId);
        assertThat(chainsConfig, containsLineWithPattern(".*\\.id \"testSearcher@default\"$"));
        assertThat(chainsConfig, not(containsLineWithPattern(".*\\.id \"testProcessor@default\"$")));
    }

    @Test
    void processingHandler_gets_only_processing_chains_in_chains_config()  {
        createClusterWithProcessingAndSearchChains();
        String processingHandlerConfigId = "default/component/" + PROCESSING_HANDLER_CLASS;
        String chainsConfig = getChainsConfig(processingHandlerConfigId);
        assertThat(chainsConfig, containsLineWithPattern(".*\\.id \"testProcessor@default\"$"));
        assertThat(chainsConfig, not(containsLineWithPattern(".*\\.id \"testSearcher@default\"$")));
    }

    @Test
    void processingHandler_is_instantiated_from_the_default_bundle() {
        createClusterWithProcessingAndSearchChains();
        ComponentsConfig.Components config = getComponentInConfig(componentsConfig(), PROCESSING_HANDLER_CLASS);
        assertEquals(PROCESSING_HANDLER_CLASS, config.bundle());
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
    void user_config_can_be_overridden_on_node() {
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
        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        assertEquals(2, cluster.getContainers().size());
        assertEquals(root.getConfig(QrMonitorConfig.class, "default/container.0").requesttimeout(), 111);
        assertEquals(root.getConfig(QrMonitorConfig.class, "default/container.1").requesttimeout(), 222);
    }

    @Test
    void component_includes_are_added() {
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/application/include_dirs");
        VespaModel model = creator.create(true);
        ContainerCluster cluster = model.getContainerClusters().get("default");
        Map<ComponentId, Component<?, ?>> componentsMap = cluster.getComponentsMap();
        Component<?, ?> example = componentsMap.get(
                ComponentId.fromString("test.Exampledocproc"));
        assertEquals("test.Exampledocproc", example.getComponentId().getName());
    }

    @Test
    void affinity_is_set() {
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
        assertEquals(0, getContainerCluster("default").getContainers().get(0).getAffinity().get().cpuSocket());
    }

    @Test
    void singlenode_servicespec_is_used_with_hosts_xml() throws IOException, SAXException {
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
        assertEquals(1, model.hostSystem().getHosts().size());
    }

    @Test
    void endpoints_are_added_to_containers() throws IOException, SAXException {
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
                .endpoints(Set.of(new ContainerEndpoint("comics-search", ApplicationClusterEndpoint.Scope.global, List.of("nalle", "balle"))))
                .properties(new TestProperties().setHostedVespa(true))
                .build();

        final var model = new VespaModel(new NullConfigModelRegistry(), deployState);
        final var containers = model.getContainerClusters().values().stream()
                .flatMap(cluster -> cluster.getContainers().stream())
                .collect(Collectors.toList());

        assertFalse(containers.isEmpty(), "Missing container objects based on configuration");

        containers.forEach(container -> {
            final var rotations = container.getServicePropertyString("rotations").split(",");
            final var rotationsSet = Set.of(rotations);
            assertEquals(Set.of("balle", "nalle"), rotationsSet);
        });
    }

    @Test
    void singlenode_servicespec_is_used_with_hosted_vespa() throws IOException, SAXException {
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

    @Test
    void cloud_account_without_nodes_tag() throws Exception {
        String servicesXml = "<container id='default' version='1.0' />";
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        CloudAccount cloudAccount = CloudAccount.from("000000000000");
        InMemoryProvisioner provisioner = new InMemoryProvisioner(true, false, "host1.yahoo.com", "host2.yahoo.com");
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .modelHostProvisioner(provisioner)
                .provisioned(provisioner.startProvisionedRecording())
                .applicationPackage(applicationPackage)
                .properties(new TestProperties().setMultitenant(true)
                                                .setHostedVespa(true)
                                                .setCloudAccount(cloudAccount))
                .build());
        assertEquals(2, model.hostSystem().getHosts().size());
        assertEquals(List.of(cloudAccount), model.provisioned().all().values()
                                                 .stream()
                                                 .map(capacity -> capacity.cloudAccount().get())
                                                 .collect(Collectors.toList()));
    }

    @Test
    void renderers_named_JsonRenderer_are_not_allowed() {
        assertThrows(IllegalArgumentException.class, () -> {
            createModel(root, generateContainerElementWithRenderer("JsonRenderer"));
        });
    }

    @Test
    void renderers_named_DefaultRenderer_are_not_allowed() {
        assertThrows(IllegalArgumentException.class, () -> {
            createModel(root, generateContainerElementWithRenderer("XmlRenderer"));
        });
    }

    @Test
    void renderers_named_something_else_are_allowed() {
        createModel(root, generateContainerElementWithRenderer("my-little-renderer"));
    }

    @Test
    void vip_status_handler_uses_file_for_hosted_vespa() throws Exception {
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
    void qrconfig_is_produced() throws IOException, SAXException {
        QrConfig qr = getQrConfig(new TestProperties());
        String hostname = HostName.getLocalhost();  // Using the same way of getting hostname as filedistribution model
        assertEquals("default.container.0", qr.discriminator());
        assertEquals(19102, qr.rpc().port());
        assertEquals("vespa/service/default/container.0", qr.rpc().slobrokId());
        assertTrue(qr.rpc().enabled());
        assertEquals("", qr.rpc().host());
        assertFalse(qr.restartOnDeploy());
        assertEquals("filedistribution/" + hostname, qr.filedistributor().configid());
        assertEquals(50.0, qr.shutdown().timeout(), 0.00000000000001);
        assertFalse(qr.shutdown().dumpHeapOnTimeout());
    }

    private QrConfig getQrConfig(ModelContext.Properties properties) throws IOException, SAXException {
        String servicesXml =
                "<services>" +
                "  <admin version='3.0'>" +
                "    <nodes count='2'/>" +
                "  </admin>" +
                "  <container id ='default' version='1.0'>" +
                "    <nodes>" +
                "      <node hostalias='node1' />" +
                "    </nodes>" +
                "  </container>" +
                "</services>";

        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .properties(properties)
                .build());

        return model.getConfig(QrConfig.class, "default/container.0");
    }

    @Test
    void control_container_shutdown() throws IOException, SAXException {
        QrConfig qr = getQrConfig(new TestProperties().containerShutdownTimeout(133).containerDumpHeapOnShutdownTimeout(true));
        assertEquals(133.0, qr.shutdown().timeout(), 0.00000000000001);
        assertTrue(qr.shutdown().dumpHeapOnTimeout());
    }

    @Test
    void environment_vars_are_honoured() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <nodes>",
                "    <environment-variables>",
                "      <KMP_SETTING>1</KMP_SETTING>",
                "      <valid_name>some value</valid_name>",
                "      <KMP_AFFINITY>granularity=fine,verbose,compact,1,0</KMP_AFFINITY>",
                "    </environment-variables>",
                "    <node hostalias='mockhost'/>",
                "  </nodes>",
                "</container>");
        createModel(root, clusterElem);
        var container = (AbstractService) root.getProducer("container/container.0");
        var env = container.getEnvVars();
        assertEquals("1", env.get("KMP_SETTING"));
        assertEquals("granularity=fine,verbose,compact,1,0", env.get("KMP_AFFINITY"));
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
    void requireThatAvailableProcessorsFollowFlavor() {
        verifyAvailableprocessors(false, null, 0);
        verifyAvailableprocessors(true, null, 0);
        verifyAvailableprocessors(true, new Flavor(new FlavorsConfig.Flavor.Builder().name("test-flavor").minCpuCores(9).build()), 9);
        verifyAvailableprocessors(true, new Flavor(new FlavorsConfig.Flavor.Builder().name("test-flavor").minCpuCores(1).build()), 2);
    }

    @Test
    void cluster_with_zookeeper() {
        Function<Integer, String> servicesXml = (nodeCount) -> "<container version='1.0' id='default'>" +
                "<nodes count='" + nodeCount + "'/>" +
                "<zookeeper session-timeout-seconds='30'/>" +
                "</container>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(3);
        {
            VespaModel model = tester.createModel(servicesXml.apply(3), true);
            ApplicationContainerCluster cluster = model.getContainerClusters().get("default");
            assertNotNull(cluster);
            assertComponentConfigured(cluster, "com.yahoo.vespa.curator.Curator");
            assertComponentConfigured(cluster, "com.yahoo.vespa.curator.CuratorWrapper");
            assertEquals(30, model.getConfig(CuratorConfig.class, cluster.getConfigId()).zookeeperSessionTimeoutSeconds());
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
            } catch (IllegalArgumentException ignored) {
            }
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
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Test
    void logs_deployment_spec_deprecations() throws Exception {
        String containerService = joinLines("<container id='foo' version='1.0'>",
                "  <nodes>",
                "    <node hostalias='host1' />",
                "  </nodes>",
                "</container>");
        String deploymentXml = joinLines("<deployment version='1.0'>",
                "  <prod global-service-id='foo'>",
                "    <region active='true'>us-east-1</region>",
                "  </prod>",
                "</deployment>");

        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder()
                .withServices(containerService)
                .withDeploymentSpec(deploymentXml)
                .build();

        TestLogger logger = new TestLogger();
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .zone(new Zone(Environment.prod, RegionName.from("us-east-1")))
                .properties(new TestProperties().setHostedVespa(true))
                .deployLogger(logger)
                .build();

        createModel(root, deployState, null, DomBuilderTest.parse(containerService));
        assertFalse(logger.msgs.isEmpty());
        assertEquals(Level.WARNING, logger.msgs.get(0).getFirst());
        assertEquals(Level.WARNING, logger.msgs.get(1).getFirst());
        assertEquals("Element 'prod' contains attribute 'global-service-id' deprecated since major version 7. See https://cloud.vespa.ai/en/reference/routing#deprecated-syntax",
                logger.msgs.get(0).getSecond());
        assertEquals("Element 'region' contains attribute 'active' deprecated since major version 7. See https://cloud.vespa.ai/en/reference/routing#deprecated-syntax",
                logger.msgs.get(1).getSecond());
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

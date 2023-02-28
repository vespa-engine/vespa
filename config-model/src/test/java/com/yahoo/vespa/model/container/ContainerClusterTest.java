// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.di.config.PlatformBundlesConfig;
import com.yahoo.container.handler.ThreadPoolProvider;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainer;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.search.ContainerSearch;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

import static com.yahoo.config.model.api.ApplicationClusterEndpoint.RoutingMethod.exclusive;
import static com.yahoo.config.model.api.ApplicationClusterEndpoint.RoutingMethod.shared;
import static com.yahoo.config.model.api.ApplicationClusterEndpoint.RoutingMethod.sharedLayer4;
import static com.yahoo.config.model.api.ApplicationClusterEndpoint.Scope.application;
import static com.yahoo.config.model.api.ApplicationClusterEndpoint.Scope.global;
import static com.yahoo.config.provision.SystemName.cd;
import static com.yahoo.config.provision.SystemName.main;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class ContainerClusterTest {

    @Test
    void requireThatClusterInfoIsPopulated() {
        ApplicationContainerCluster cluster = newContainerCluster();
        ClusterInfoConfig config = getClusterInfoConfig(cluster);
        assertEquals("name", config.clusterId());
        assertEquals(2, config.nodeCount());
        assertEquals(List.of(0, 0), config.nodeIndices());  // both containers are created with index 0
        assertEquals(2, config.services().size());

        Iterator<ClusterInfoConfig.Services> iterator = config.services().iterator();
        ClusterInfoConfig.Services service = iterator.next();
        assertEquals("host-c1", service.hostname());
        assertEquals(0, service.index());
        assertEquals(4, service.ports().size());

        service = iterator.next();
        assertEquals("host-c2", service.hostname());
        assertEquals(1, service.index());
        assertEquals(4, service.ports().size());
    }

    @Test
    void search_and_docproc_bundles_are_not_installed_for_plain_application_clusters() {
        ApplicationContainerCluster cluster = newContainerCluster();

        var bundleBuilder = new PlatformBundlesConfig.Builder();
        cluster.getConfig(bundleBuilder);
        List<Path> installedBundles = bundleBuilder.build().bundlePaths().stream().map(Paths::get).toList();
        installedBundles.forEach(bundle -> assertFalse(PlatformBundles.SEARCH_AND_DOCPROC_BUNDLES.contains(bundle)));
    }

    @Test
    void search_and_docproc_bundles_are_installed_for_application_clusters_with_search() {
        ApplicationContainerCluster cluster = newClusterWithSearch(createRoot(false), false, null);

        var bundleBuilder = new PlatformBundlesConfig.Builder();
        cluster.getConfig(bundleBuilder);
        List<Path> installedBundles = bundleBuilder.build().bundlePaths().stream().map(Paths::get).toList();
        PlatformBundles.SEARCH_AND_DOCPROC_BUNDLES.forEach(bundle -> assertTrue(installedBundles.contains(bundle)));
    }

    @Test
    void requireThatWeCanGetTheZoneConfig() {
        DeployState state = new DeployState.Builder().properties(new TestProperties().setHostedVespa(true))
                .zone(new Zone(SystemName.cd, Environment.test, RegionName.from("some-region")))
                .build();
        MockRoot root = new MockRoot("foo", state);
        ContainerCluster<?> cluster = new ApplicationContainerCluster(root, "container0", "container1", state);
        ConfigserverConfig.Builder builder = new ConfigserverConfig.Builder();
        cluster.getConfig(builder);
        ConfigserverConfig config = new ConfigserverConfig(builder);
        assertEquals(Environment.test.value(), config.environment());
        assertEquals("some-region", config.region());
        assertEquals("cd", config.system());
    }

    private void verifyHeapSizeAsPercentageOfPhysicalMemory(MockRoot root,
                                                            boolean isCombinedCluster,
                                                            Integer explicitMemoryPercentage,
                                                            int expectedMemoryPercentage) {
        ApplicationContainerCluster cluster = newClusterWithSearch(root, isCombinedCluster, explicitMemoryPercentage);
        QrStartConfig.Builder qsB = new QrStartConfig.Builder();
        cluster.getConfig(qsB);
        QrStartConfig qsC= new QrStartConfig(qsB);
        assertEquals(expectedMemoryPercentage, qsC.jvm().heapSizeAsPercentageOfPhysicalMemory());
        assertEquals(0, qsC.jvm().compressedClassSpaceSize());
    }

    @Test
    void requireThatHeapSizeAsPercentageOfPhysicalMemoryForHostedAndNot() {
        int heapSizeInFlag = 89;
        boolean hosted = true;
        boolean combined = true; // a cluster running on content nodes (only relevant with hosted)
        verifyHeapSizeAsPercentageOfPhysicalMemory(createRoot(hosted), !combined, null, ApplicationContainerCluster.defaultHeapSizePercentageOfTotalNodeMemory);
        verifyHeapSizeAsPercentageOfPhysicalMemory(createRoot(hosted, heapSizeInFlag), !combined, null, heapSizeInFlag);
        verifyHeapSizeAsPercentageOfPhysicalMemory(createRoot(hosted),   combined, null, 18);
        verifyHeapSizeAsPercentageOfPhysicalMemory(createRoot(hosted, heapSizeInFlag),   combined, null, 18);
        verifyHeapSizeAsPercentageOfPhysicalMemory(createRoot(!hosted), !combined, null, 0);
        verifyHeapSizeAsPercentageOfPhysicalMemory(createRoot(!hosted, heapSizeInFlag), !combined, null, 0);

        // Explicit value overrides all defaults
        verifyHeapSizeAsPercentageOfPhysicalMemory(createRoot(hosted, heapSizeInFlag), !combined, 67, 67);
        verifyHeapSizeAsPercentageOfPhysicalMemory(createRoot(hosted, heapSizeInFlag),   combined, 68, 68);
        verifyHeapSizeAsPercentageOfPhysicalMemory(createRoot(!hosted, heapSizeInFlag), !combined, 69, 69);
    }

    private void verifyJvmArgs(boolean isHosted, String expectedArgs, String jvmArgs) {
        assertEquals(expectedJvmArgs(isHosted, expectedArgs), jvmArgs);
    }

    private void verifyJvmArgs(boolean isHosted, boolean hasDocProc) {
        MockRoot root = createRoot(isHosted);
        ApplicationContainerCluster cluster = newClusterWithSearch(root);
        if (hasDocProc) {
            cluster.setDocproc(new ContainerDocproc(cluster, null));
        }
        addContainer(root, cluster, "c1", "host-c1");
        assertEquals(1, cluster.getContainers().size());
        ApplicationContainer container = cluster.getContainers().get(0);
        verifyJvmArgs(isHosted, "", container.getJvmOptions());
        container.setJvmOptions("initial");
        verifyJvmArgs(isHosted, "initial", container.getJvmOptions());
        container.prependJvmOptions("ignored");
        verifyJvmArgs(isHosted,  "ignored initial", container.getJvmOptions());
        container.appendJvmOptions("override");
        verifyJvmArgs(isHosted, "ignored initial override", container.getJvmOptions());
        container.setJvmOptions(null);
        verifyJvmArgs(isHosted,  "", container.getJvmOptions());
    }

    @Test
    void testClusterControllerResourceUsage() {
        MockRoot root = createRoot(false);
        ClusterControllerContainerCluster cluster = createClusterControllerCluster(root);
        addClusterController(cluster, "host-c1", root.getDeployState());
        assertEquals(1, cluster.getContainers().size());
        QrStartConfig.Builder qrBuilder = new QrStartConfig.Builder();
        cluster.getConfig(qrBuilder);
        QrStartConfig qrStartConfig = new QrStartConfig(qrBuilder);
        assertEquals(32, qrStartConfig.jvm().minHeapsize());
        assertEquals(128, qrStartConfig.jvm().heapsize());
        assertEquals(32, qrStartConfig.jvm().compressedClassSpaceSize());
        assertEquals(0, qrStartConfig.jvm().heapSizeAsPercentageOfPhysicalMemory());
        root.freezeModelTopology();

        ThreadpoolConfig threadpoolConfig = root.getConfig(ThreadpoolConfig.class, "container0/component/default-threadpool");
        assertEquals(1, threadpoolConfig.maxthreads());
        assertEquals(50, threadpoolConfig.queueSize());
    }

    @Test
    void search_and_docproc_bundles_are_not_installed_for_cluster_controllers() {
        MockRoot root = createRoot(false);
        ClusterControllerContainerCluster cluster = createClusterControllerCluster(root);

        var bundleBuilder = new PlatformBundlesConfig.Builder();
        cluster.getConfig(bundleBuilder);
        List<Path> installedBundles = bundleBuilder.build().bundlePaths().stream().map(Paths::get).toList();
        installedBundles.forEach(bundle -> assertFalse(PlatformBundles.SEARCH_AND_DOCPROC_BUNDLES.contains(bundle)));
    }

    @Test
    void testThatLinguisticsIsExcludedForClusterControllerCluster() {
        MockRoot root = createRoot(false);
        ClusterControllerContainerCluster cluster = createClusterControllerCluster(root);
        addClusterController(cluster, "host-c1", root.getDeployState());
        assertFalse(contains("com.yahoo.language.provider.DefaultLinguisticsProvider", cluster.getAllComponents()));
    }

    private static boolean contains(String componentId, Collection<Component<?, ?>> componentList) {
        for (Component<?, ?> component : componentList)
            if (component.getClassId().toId().getName().equals(componentId))
                return true;
        return false;
    }

    @Test
    void requireThatJvmArgsControlWorksForHostedAndNot() {
        verifyJvmArgs(true, false);
        verifyJvmArgs(true, true);
        verifyJvmArgs(false, false);
        verifyJvmArgs(false, true);
    }

    @Test
    void requireThatJvmOmitStackTraceInFastThrowOptionWorks() {
        // Empty option if option not set in property
        MockRoot root = createRoot(new DeployState.Builder().build());
        ApplicationContainerCluster cluster = newClusterWithSearch(root);
        addContainer(root, cluster, "c1", "host-c1");
        ApplicationContainer container = cluster.getContainers().get(0);
        assertEquals("", container.getJvmOptions());

        String jvmOption = "-XX:-foo";
        DeployState deployState = new DeployState.Builder().properties(new TestProperties().setJvmOmitStackTraceInFastThrowOption(jvmOption)).build();
        root = createRoot(deployState);
        cluster = newClusterWithSearch(root);
        addContainer(root, cluster, "c1", "host-c1");
        container = cluster.getContainers().get(0);
        assertEquals(jvmOption, container.getJvmOptions());
    }

    @Test
    void requireThatWeCanHandleNullJvmOptions() {
        MockRoot root = createRoot(false);
        ApplicationContainerCluster cluster = newClusterWithSearch(root);
        addContainer(root, cluster, "c1", "host-c1");
        Container container = cluster.getContainers().get(0);
        container.setJvmOptions("");
        String empty = container.getJvmOptions();
        container.setJvmOptions(null);
        assertEquals(empty, container.getJvmOptions());
    }

    @Test
    void requireThatNonHostedUsesExpectedDefaultThreadpoolConfiguration() {
        MockRoot root = new MockRoot("foo");
        ApplicationContainerCluster cluster = newClusterWithSearch(root);
        addContainer(root, cluster, "c1", "host-c1");
        root.freezeModelTopology();

        ThreadpoolConfig threadpoolConfig = root.getConfig(ThreadpoolConfig.class, "container0/component/default-threadpool");
        assertEquals(-100, threadpoolConfig.maxthreads());
        assertEquals(0, threadpoolConfig.queueSize());
    }

    @Test
    void container_cluster_has_default_threadpool_provider() {
        MockRoot root = new MockRoot("foo");
        ApplicationContainerCluster cluster = newClusterWithSearch(root);
        addContainer(root, cluster, "c1", "host-c1");
        root.freezeModelTopology();

        ComponentId expectedComponentId = new ComponentId("default-threadpool");
        var components = cluster.getComponentsMap();
        assertTrue(components.containsKey(expectedComponentId));
        Component<?, ?> component = components.get(expectedComponentId);
        assertEquals(ThreadPoolProvider.class.getName(), component.getClassId().getName());
    }

    @Test
    void config_for_default_threadpool_provider_scales_with_node_resources_in_hosted() {
        MockRoot root = new MockRoot(
                "foo",
                new DeployState.Builder()
                        .properties(new TestProperties().setHostedVespa(true))
                        .applicationPackage(new MockApplicationPackage.Builder().build())
                        .build());
        ApplicationContainerCluster cluster = newClusterWithSearch(root);
        addContainer(root, cluster, "c1", "host-c1");
        root.freezeModelTopology();

        ThreadpoolConfig threadpoolConfig = root.getConfig(ThreadpoolConfig.class, "container0/component/default-threadpool");
        assertEquals(-100, threadpoolConfig.maxthreads());
        assertEquals(0, threadpoolConfig.queueSize());
    }

    @Test
    void jetty_threadpool_scales_with_node_resources_in_hosted() {
        MockRoot root = new MockRoot(
                "foo",
                new DeployState.Builder()
                        .properties(new TestProperties().setHostedVespa(true))
                        .applicationPackage(new MockApplicationPackage.Builder().build())
                        .build());
        ApplicationContainerCluster cluster = newClusterWithSearch(root);
        addContainer(root, cluster, "c1", "host-c1");
        root.freezeModelTopology();

        ServerConfig cfg = root.getConfig(ServerConfig.class, "container0/c1/DefaultHttpServer");
        assertEquals(-1, cfg.maxWorkerThreads()); // Scale with cpu count observed by JVM
        assertEquals(-1, cfg.minWorkerThreads()); // Scale with cpu count observed by JVM
    }

    @Test
    void requireThatBundlesForTesterApplicationAreInstalled() {
        List<String> expectedOnpremBundles =
                List.of("vespa-testrunner-components-jar-with-dependencies.jar",
                        "vespa-osgi-testrunner-jar-with-dependencies.jar",
                        "tenant-cd-api-jar-with-dependencies.jar");
        verifyTesterApplicationInstalledBundles(Zone.defaultZone(), expectedOnpremBundles);

        List<String> expectedPublicBundles = new ArrayList<>(expectedOnpremBundles);
        expectedPublicBundles.add("cloud-tenant-cd-jar-with-dependencies.jar");
        Zone publicZone = new Zone(SystemName.PublicCd, Environment.dev, RegionName.defaultName());
        verifyTesterApplicationInstalledBundles(publicZone, expectedPublicBundles);

    }

    @Test
    void requireCuratorConfig() {
        DeployState state = new DeployState.Builder().build();
        MockRoot root = new MockRoot("foo", state);
        var cluster = new ApplicationContainerCluster(root, "container", "search-cluster", state);
        addContainer(root, cluster, "c1", "host-c1");
        addContainer(root, cluster, "c2", "host-c2");
        CuratorConfig.Builder configBuilder = new CuratorConfig.Builder();
        cluster.getConfig(configBuilder);
        CuratorConfig config = configBuilder.build();
        assertEquals(List.of("host-c1", "host-c2"),
                config.server().stream().map(CuratorConfig.Server::hostname).toList());
        assertTrue(config.zookeeperLocalhostAffinity());
        assertEquals(30, config.zookeeperSessionTimeoutSeconds());
    }

    @Test
    void requireZooKeeperServerConfig() {
        DeployState state = new DeployState.Builder().build();
        MockRoot root = new MockRoot("foo", state);
        var cluster = new ApplicationContainerCluster(root, "container", "search-cluster", state);
        addContainer(root, cluster, "c1", "host-c1");
        addContainer(root, cluster, "c2", "host-c2");
        addContainer(root, cluster, "c3", "host-c3");

        // Only myid is set for container
        ZookeeperServerConfig.Builder configBuilder = new ZookeeperServerConfig.Builder();
        cluster.getContainers().get(0).getConfig(configBuilder);
        assertEquals(0, configBuilder.build().myid());

        // the rest (e.g. servers) is set for cluster
        cluster.getConfig(configBuilder);
        assertEquals(0, configBuilder.build().myid());
        assertEquals(List.of("host-c1", "host-c2", "host-c3"),
                configBuilder.build().server().stream().map(ZookeeperServerConfig.Server::hostname).toList());

    }

    @Test
    void generatesCorrectRoutingInfo() {
        // main system:
        assertNames(main,
                ApplicationId.from("t1", "a1", "i1"),
                Set.of(),
                List.of("search-cluster.i1.a1.t1.endpoint.suffix"));

        assertNames(main,
                ApplicationId.from("t1", "a1", "default"),
                Set.of(),
                List.of("search-cluster.a1.t1.endpoint.suffix"));

        assertNames(main,
                ApplicationId.from("t1", "default", "default"),
                Set.of(),
                List.of("search-cluster.default.t1.endpoint.suffix"));

        assertNames(main,
                ApplicationId.from("t1", "a1", "default"),
                Set.of(new ContainerEndpoint("not-in-this-cluster", global, List.of("foo", "bar"))),
                List.of("search-cluster.a1.t1.endpoint.suffix"));

        assertNames(main,
                ApplicationId.from("t1", "a1", "default"),
                Set.of(new ContainerEndpoint("search-cluster", global, List.of("rotation-1.x.y.z", "rotation-2.x.y.z"), OptionalInt.empty(), sharedLayer4),
                        new ContainerEndpoint("search-cluster", application, List.of("app-rotation.x.y.z"), OptionalInt.of(3), sharedLayer4)),
                List.of("search-cluster.a1.t1.endpoint.suffix", "rotation-1.x.y.z", "rotation-2.x.y.z", "app-rotation.x.y.z"));

        // cd system:
        assertNames(cd,
                ApplicationId.from("t1", "a1", "i1"),
                Set.of(),
                List.of("search-cluster.cd.i1.a1.t1.endpoint.suffix"));

        assertNames(cd,
                ApplicationId.from("t1", "a1", "default"),
                Set.of(),
                List.of("search-cluster.cd.a1.t1.endpoint.suffix"));

        assertNames(cd,
                ApplicationId.from("t1", "default", "default"),
                Set.of(),
                List.of("search-cluster.cd.default.t1.endpoint.suffix"));

        assertNames(cd,
                ApplicationId.from("t1", "a1", "default"),
                Set.of(new ContainerEndpoint("not-in-this-cluster", global, List.of("foo", "bar"))),
                List.of("search-cluster.cd.a1.t1.endpoint.suffix"));

        assertNames(cd,
                ApplicationId.from("t1", "a1", "default"),
                Set.of(new ContainerEndpoint("search-cluster", global, List.of("rotation-1.x.y.z", "rotation-2.x.y.z"), OptionalInt.empty(), sharedLayer4),
                        new ContainerEndpoint("search-cluster", global, List.of("a.b.x.y.z", "rotation-2.x.y.z"), OptionalInt.empty(), shared),
                        new ContainerEndpoint("search-cluster", application, List.of("app-rotation.x.y.z"), OptionalInt.of(3), sharedLayer4),
                        new ContainerEndpoint("not-supported", global, List.of("not.supported"), OptionalInt.empty(), exclusive)),
                List.of("search-cluster.cd.a1.t1.endpoint.suffix", "rotation-1.x.y.z", "rotation-2.x.y.z", "app-rotation.x.y.z"));

    }

    private void assertNames(SystemName systemName, ApplicationId appId, Set<ContainerEndpoint> globalEndpoints, List<String> expectedSharedL4Names) {
        Zone zone = new Zone(systemName, Environment.defaultEnvironment(), RegionName.defaultName());
        DeployState state = new DeployState.Builder()
                .zone(zone)
                .endpoints(globalEndpoints)
                .properties(new TestProperties()
                                    .setHostedVespa(true)
                                    .setApplicationId(appId)
                                    .setZoneDnsSuffixes(List.of(".endpoint.suffix")))
                .build();
        MockRoot root = new MockRoot("foo", state);
        ApplicationContainerCluster cluster = new ApplicationContainerCluster(root, "container", "search-cluster", state);
        addContainer(root, cluster, "c1", "host-c1");
        cluster.doPrepare(state);
        List<ApplicationClusterEndpoint> endpoints = cluster.endpoints();

        assertNames(List.of(), endpoints.stream().filter(e -> e.routingMethod() == shared).toList());
        assertNames(expectedSharedL4Names, endpoints.stream().filter(e -> e.routingMethod() == sharedLayer4).toList());

        List<ContainerEndpoint> endpointsWithWeight =
                globalEndpoints.stream().filter(endpoint -> endpoint.weight().isPresent()).toList();
        endpointsWithWeight.stream()
                .filter(ce -> ce.weight().isPresent())
                .forEach(ce -> assertTrue(endpointsMatch(ce, endpoints)));
    }

    private void assertNames(List<String> expectedNames, List<ApplicationClusterEndpoint> endpoints) {
        assertEquals(expectedNames.size(), endpoints.size());
        expectedNames.forEach(expected -> assertTrue(endpoints.stream().anyMatch(e -> Objects.equals(e.dnsName().value(), expected)), "Endpoint not matched " + expected + " was: " + endpoints));
    }

    private boolean endpointsMatch(ContainerEndpoint configuredEndpoint, List<ApplicationClusterEndpoint> clusterEndpoints) {
        return clusterEndpoints.stream().anyMatch(e ->
            configuredEndpoint.names().contains(e.dnsName().value()) &&
            configuredEndpoint.weight().getAsInt() == e.weight());
    }

    private void verifyTesterApplicationInstalledBundles(Zone zone, List<String> expectedBundleNames) {
        ApplicationId appId = ApplicationId.from("tenant", "application", "instance-t");
        DeployState state = new DeployState.Builder().properties(
                new TestProperties()
                        .setHostedVespa(true)
                        .setApplicationId(appId))
                .zone(zone).build();
        MockRoot root = new MockRoot("foo", state);
        ApplicationContainerCluster cluster = new ApplicationContainerCluster(root, "container0", "container1", state);
        var bundleBuilder = new PlatformBundlesConfig.Builder();
        cluster.getConfig(bundleBuilder);
        List<String> installedBundles = bundleBuilder.build().bundlePaths();

        expectedBundleNames.forEach(b -> assertTrue(installedBundles.stream().anyMatch(p -> p.endsWith(b))));
    }

    private static ApplicationContainerCluster newClusterWithSearch(MockRoot root) {
        return newClusterWithSearch(root, false, null);
    }

    private static ApplicationContainerCluster newClusterWithSearch(MockRoot root, boolean isCombinedCluster, Integer memoryPercentage) {
        ApplicationContainerCluster cluster = new ApplicationContainerCluster(root, "container0", "container1", root.getDeployState());
        if (isCombinedCluster)
            cluster.setHostClusterId("test-content-cluster");
        cluster.setMemoryPercentage(memoryPercentage);
        cluster.setSearch(new ContainerSearch(root.getDeployState(), cluster, new SearchChains(cluster, "search-chain")));
        return cluster;
    }

    private static ClusterControllerContainerCluster createClusterControllerCluster(MockRoot root) {
        return new ClusterControllerContainerCluster(root, "container0", "container1", root.getDeployState());
    }

    private static MockRoot createRoot(boolean isHosted) {
        DeployState state = new DeployState.Builder().properties(new TestProperties().setHostedVespa(isHosted)).build();
        return createRoot(state);
    }
    private static MockRoot createRoot(boolean isHosted, int heapSizePercentage) {
        DeployState state = new DeployState.Builder().properties(
                new TestProperties()
                        .setHostedVespa(isHosted)
                        .setHeapSizePercentage(heapSizePercentage)).build();
        return createRoot(state);
    }

    private static MockRoot createRoot(DeployState deployState) {
        return new MockRoot("foo", deployState);
    }

    private static void addContainer(MockRoot root, ApplicationContainerCluster cluster, String name, String hostName) {
        addContainerWithHostResource(root, cluster, name, new HostResource(new Host(null, hostName)));
    }

    private static void addContainerWithHostResource(MockRoot root,
                                                     ApplicationContainerCluster cluster,
                                                     String name,
                                                     HostResource hostResource) {
        ApplicationContainer container = new ApplicationContainer(cluster, name, 0, root.getDeployState());
        container.setHostResource(hostResource);
        container.initService(root.getDeployState());
        cluster.addContainer(container);
    }

    private static void addClusterController(ClusterControllerContainerCluster cluster,
                                             String hostName,
                                             DeployState deployState) {
        ClusterControllerContainer container = new ClusterControllerContainer(cluster, 1, false, deployState, false);
        container.setHostResource(new HostResource(new Host(null, hostName)));
        container.initService(deployState);
        cluster.addContainer(container);
    }

    private static ApplicationContainerCluster newContainerCluster() {
        DeployState deployState = DeployState.createTestState();
        MockRoot root = new MockRoot("foo", deployState);
        ApplicationContainerCluster cluster = new ApplicationContainerCluster(root, "subId", "name", deployState);
        addContainer(root, cluster, "c1", "host-c1");
        addContainer(root, cluster, "c2", "host-c2");
        return cluster;
    }

    private static ClusterInfoConfig getClusterInfoConfig(ContainerCluster<?> cluster) {
        ClusterInfoConfig.Builder builder = new ClusterInfoConfig.Builder();
        cluster.getConfig(builder);
        return new ClusterInfoConfig(builder);
    }

    private static String expectedJvmArgs(boolean isHosted, String extra) {
        if (!isHosted) return extra;
        return "-Djdk.tls.server.enableStatusRequestExtension=true " +
                "-Djdk.tls.stapling.responseTimeout=2000 " +
                "-Djdk.tls.stapling.cacheSize=256 " +
                "-Djdk.tls.stapling.cacheLifetime=3600" +
                (extra.isEmpty() ? "" : " " + extra);
    }

}

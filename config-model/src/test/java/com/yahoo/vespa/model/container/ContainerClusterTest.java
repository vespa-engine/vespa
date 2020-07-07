// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.RoutingProviderConfig;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.container.di.PlatformBundlesConfig;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainer;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.search.ContainerSearch;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

/**
 * @author Simon Thoresen Hult
 */
public class ContainerClusterTest {

    @Test
    public void requireThatClusterInfoIsPopulated() {
        ApplicationContainerCluster cluster = newContainerCluster();
        ClusterInfoConfig config = getClusterInfoConfig(cluster);
        assertEquals("name", config.clusterId());
        assertEquals(2, config.nodeCount());
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
    public void requreThatWeCanGetTheZoneConfig() {
        DeployState state = new DeployState.Builder().properties(new TestProperties().setHostedVespa(true))
                                                     .zone(new Zone(SystemName.cd, Environment.test, RegionName.from("some-region")))
                                                     .build();
        MockRoot root = new MockRoot("foo", state);
        ContainerCluster cluster = new ApplicationContainerCluster(root, "container0", "container1", state);
        ConfigserverConfig.Builder builder = new ConfigserverConfig.Builder();
        cluster.getConfig(builder);
        ConfigserverConfig config = new ConfigserverConfig(builder);
        assertEquals(Environment.test.value(), config.environment());
        assertEquals("some-region", config.region());
        assertEquals("cd", config.system());
    }

    private ApplicationContainerCluster createContainerCluster(MockRoot root, boolean isCombinedCluster) {
        return createContainerCluster(root, isCombinedCluster, null);
    }
    private ApplicationContainerCluster createContainerCluster(MockRoot root, boolean isCombinedCluster, Integer memoryPercentage) {
        ApplicationContainerCluster cluster = new ApplicationContainerCluster(root, "container0", "container1", root.getDeployState());
        if (isCombinedCluster)
            cluster.setHostClusterId("test-content-cluster");
        cluster.setMemoryPercentage(memoryPercentage);
        cluster.setSearch(new ContainerSearch(cluster, new SearchChains(cluster, "search-chain"), new ContainerSearch.Options()));
        return cluster;
    }
    private ClusterControllerContainerCluster createClusterControllerCluster(MockRoot root) {
        return new ClusterControllerContainerCluster(root, "container0", "container1", root.getDeployState());
    }
    private MockRoot createRoot(boolean isHosted) {
        DeployState state = new DeployState.Builder().properties(new TestProperties().setHostedVespa(isHosted)).build();
        return new MockRoot("foo", state);
    }

    private void verifyHeapSizeAsPercentageOfPhysicalMemory(boolean isHosted, boolean isCombinedCluster,
                                                            Integer explicitMemoryPercentage,
                                                            int expectedMemoryPercentage) {
        ContainerCluster cluster = createContainerCluster(createRoot(isHosted), isCombinedCluster, explicitMemoryPercentage);
        QrStartConfig.Builder qsB = new QrStartConfig.Builder();
        cluster.getConfig(qsB);
        QrStartConfig qsC= new QrStartConfig(qsB);
        assertEquals(expectedMemoryPercentage, qsC.jvm().heapSizeAsPercentageOfPhysicalMemory());
        assertEquals(0, qsC.jvm().compressedClassSpaceSize());
    }

    @Test
    public void requireThatHeapSizeAsPercentageOfPhysicalMemoryForHostedAndNot() {
        boolean hosted = true;
        boolean combined = true; // a cluster running on content nodes (only relevant with hosted)
        verifyHeapSizeAsPercentageOfPhysicalMemory(  hosted, ! combined, null, 60);
        verifyHeapSizeAsPercentageOfPhysicalMemory(  hosted,   combined, null, 17);
        verifyHeapSizeAsPercentageOfPhysicalMemory(! hosted, ! combined, null, 0);
        
        // Explicit value overrides all defaults
        verifyHeapSizeAsPercentageOfPhysicalMemory(  hosted, ! combined, 67, 67);
        verifyHeapSizeAsPercentageOfPhysicalMemory(  hosted,   combined, 68, 68);
        verifyHeapSizeAsPercentageOfPhysicalMemory(! hosted, ! combined, 69, 69);
    }

    private void verifyJvmArgs(boolean isHosted, boolean hasDocproc, String expectedArgs, String jvmArgs) {
        if (isHosted && hasDocproc) {
            String defaultHostedJVMArgs = "-XX:+UseOSErrorReporting -XX:+SuppressFatalErrorMessage";
            if ( ! "".equals(expectedArgs)) {
                defaultHostedJVMArgs = defaultHostedJVMArgs + " ";
            }
            assertEquals(defaultHostedJVMArgs + expectedArgs, jvmArgs);
        } else {
            assertEquals(expectedArgs, jvmArgs);
        }
    }

    private void verifyJvmArgs(boolean isHosted, boolean hasDocProc) {
        MockRoot root = createRoot(isHosted);
        ApplicationContainerCluster cluster = createContainerCluster(root, false);
        if (hasDocProc) {
            cluster.setDocproc(new ContainerDocproc(cluster, null));
        }
        addContainer(root.deployLogger(), cluster, "c1", "host-c1");
        assertEquals(1, cluster.getContainers().size());
        ApplicationContainer container = cluster.getContainers().get(0);
        verifyJvmArgs(isHosted, hasDocProc, "", container.getJvmOptions());
        container.setJvmOptions("initial");
        verifyJvmArgs(isHosted, hasDocProc, "initial", container.getJvmOptions());
        container.prependJvmOptions("ignored");
        verifyJvmArgs(isHosted, hasDocProc, "ignored initial", container.getJvmOptions());
        container.appendJvmOptions("override");
        verifyJvmArgs(isHosted, hasDocProc, "ignored initial override", container.getJvmOptions());
        container.setJvmOptions(null);
        verifyJvmArgs(isHosted, hasDocProc, "", container.getJvmOptions());
    }

    @Test
    public void testClusterControllerResourceUsage() {
        MockRoot root = createRoot(false);
        ClusterControllerContainerCluster cluster = createClusterControllerCluster(root);
        addClusterController(root.deployLogger(), cluster, "host-c1");
        assertEquals(1, cluster.getContainers().size());
        QrStartConfig.Builder qrBuilder = new QrStartConfig.Builder();
        cluster.getConfig(qrBuilder);
        QrStartConfig qrStartConfig = new QrStartConfig(qrBuilder);
        assertEquals(32, qrStartConfig.jvm().minHeapsize());
        assertEquals(512, qrStartConfig.jvm().heapsize());
        assertEquals(32, qrStartConfig.jvm().compressedClassSpaceSize());
        assertEquals(0, qrStartConfig.jvm().heapSizeAsPercentageOfPhysicalMemory());

        ThreadpoolConfig.Builder tpBuilder = new ThreadpoolConfig.Builder();
        cluster.getConfig(tpBuilder);
        ThreadpoolConfig threadpoolConfig = new ThreadpoolConfig(tpBuilder);
        assertEquals(10, threadpoolConfig.maxthreads());
        assertEquals(0, threadpoolConfig.queueSize());
    }

    @Test
    public void testThatLinguisticsIsExcludedForClusterControllerCluster() {
        MockRoot root = createRoot(false);
        ClusterControllerContainerCluster cluster = createClusterControllerCluster(root);
        addClusterController(root.deployLogger(), cluster, "host-c1");
        assertFalse(contains("com.yahoo.language.provider.DefaultLinguisticsProvider", cluster.getAllComponents()));
    }

    private static boolean contains(String componentId, Collection<Component<?, ?>> componentList) {
        for (Component<?, ?> component : componentList)
            if (component.getClassId().toId().getName().equals(componentId))
                return true;
        return false;
    }

    @Test
    public void requireThatJvmArgsControlWorksForHostedAndNot() {
        verifyJvmArgs(true, false);
        verifyJvmArgs(true, true);
        verifyJvmArgs(false, false);
        verifyJvmArgs(false, true);
    }

    @Test
    public void requireThatWeCanhandleNull() {
        MockRoot root = createRoot(false);
        ApplicationContainerCluster cluster = createContainerCluster(root, false);
        addContainer(root.deployLogger(), cluster, "c1", "host-c1");
        Container container = cluster.getContainers().get(0);
        container.setJvmOptions("");
        String empty = container.getJvmOptions();
        container.setJvmOptions(null);
        assertEquals(empty, container.getJvmOptions());
    }

    @Test
    public void requireThatPoolAndQueueCanNotBeControlledByPropertiesWhenNoFlavor() {
        DeployState state = new DeployState.Builder().properties(new TestProperties()
                    .setThreadPoolSizeFactor(8.5)
                    .setQueueSizeFactor(13.3))
                .build();
        MockRoot root = new MockRoot("foo", state);
        ApplicationContainerCluster cluster = createContainerCluster(root, false);
        addContainer(root.deployLogger(), cluster, "c1", "host-c1");

        ThreadpoolConfig.Builder tpBuilder = new ThreadpoolConfig.Builder();
        cluster.getConfig(tpBuilder);
        ThreadpoolConfig threadpoolConfig = new ThreadpoolConfig(tpBuilder);
        assertEquals(500, threadpoolConfig.maxthreads());
        assertEquals(0, threadpoolConfig.queueSize());
    }

    @Test
    public void requireThatPoolAndQueueCanBeControlledByPropertiesAndFlavor() {
        FlavorsConfig.Flavor.Builder flavorBuilder = new FlavorsConfig.Flavor.Builder().name("my_flavor").minCpuCores(3);
        NodeResourcesTuning nodeResourcesTuning = new NodeResourcesTuning(new Flavor(new FlavorsConfig.Flavor(flavorBuilder)).resources())
                .setThreadPoolSizeFactor(13.3)
                .setQueueSizeFactor(17.5);

        ThreadpoolConfig.Builder tpBuilder = new ThreadpoolConfig.Builder();
        nodeResourcesTuning.getConfig(tpBuilder);
        ThreadpoolConfig threadpoolConfig = new ThreadpoolConfig(tpBuilder);
        assertEquals(40, threadpoolConfig.maxthreads());
        assertEquals(700, threadpoolConfig.queueSize());
    }

    @Test
    public void requireThatDefaultThreadPoolConfigIsSane() {
        MockRoot root = new MockRoot("foo");
        ApplicationContainerCluster cluster = createContainerCluster(root, false);
        addContainer(root.deployLogger(), cluster, "c1", "host-c1");

        ThreadpoolConfig.Builder tpBuilder = new ThreadpoolConfig.Builder();
        cluster.getConfig(tpBuilder);
        ThreadpoolConfig threadpoolConfig = new ThreadpoolConfig(tpBuilder);
        assertEquals(500, threadpoolConfig.maxthreads());
        assertEquals(0, threadpoolConfig.queueSize());
    }

    @Test
    public void requireThatRoutingProviderIsDisabledForNonHosted() {
        DeployState state = new DeployState.Builder().properties(new TestProperties().setHostedVespa(false)).build();
        MockRoot root = new MockRoot("foo", state);
        ApplicationContainerCluster cluster = new ApplicationContainerCluster(root, "container0", "container1", state);
        RoutingProviderConfig.Builder builder = new RoutingProviderConfig.Builder();
        cluster.getConfig(builder);
        RoutingProviderConfig config = new RoutingProviderConfig(builder);
        assertFalse(config.enabled());
    }

    @Test
    public void requireThatBundlesForTesterApplicationAreInstalled() {
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
        List<String> installedBundles = bundleBuilder.build().bundles().stream().map(FileReference::value).collect(Collectors.toList());

        assertEquals(expectedBundleNames.size(), installedBundles.size());
        assertThat(installedBundles, containsInAnyOrder(
                expectedBundleNames.stream().map(CoreMatchers::endsWith).collect(Collectors.toList())
        ));
    }


    private static void addContainer(DeployLogger deployLogger, ApplicationContainerCluster cluster, String name, String hostName) {
        ApplicationContainer container = new ApplicationContainer(cluster, name, 0, cluster.isHostedVespa());
        container.setHostResource(new HostResource(new Host(null, hostName)));
        container.initService(deployLogger);
        cluster.addContainer(container);
    }

    private static void addClusterController(DeployLogger deployLogger, ClusterControllerContainerCluster cluster, String hostName) {
        ClusterControllerContainer container = new ClusterControllerContainer(cluster, 1, false, cluster.isHostedVespa());
        container.setHostResource(new HostResource(new Host(null, hostName)));
        container.initService(deployLogger);
        cluster.addContainer(container);
    }

    private static ApplicationContainerCluster newContainerCluster() {
        DeployState deployState = DeployState.createTestState();
        MockRoot root = new MockRoot("foo", deployState);
        ApplicationContainerCluster cluster = new ApplicationContainerCluster(root, "subId", "name", deployState);
        addContainer(deployState.getDeployLogger(), cluster, "c1", "host-c1");
        addContainer(deployState.getDeployLogger(), cluster, "c2", "host-c2");
        return cluster;
    }

    private static ClusterInfoConfig getClusterInfoConfig(ContainerCluster cluster) {
        ClusterInfoConfig.Builder builder = new ClusterInfoConfig.Builder();
        cluster.getConfig(builder);
        return new ClusterInfoConfig(builder);
    }

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.RoutingProviderConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.container.jdisc.config.MetricDefaultsConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerClusterVerifier;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainer;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.search.ContainerSearch;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import org.junit.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ContainerClusterTest {

    @Test
    public void requireThatDefaultMetricConsumerFactoryCanBeConfigured() {
        ContainerCluster cluster = newContainerCluster();
        cluster.setDefaultMetricConsumerFactory(MetricDefaultsConfig.Factory.Enum.YAMAS_SCOREBOARD);
        assertEquals(MetricDefaultsConfig.Factory.Enum.YAMAS_SCOREBOARD,
                getMetricDefaultsConfig(cluster).factory());
    }

    @Test
    public void requireThatDefaultMetricConsumerFactoryMatchesConfigDefault() {
        ContainerCluster cluster = newContainerCluster();
        assertEquals(new MetricDefaultsConfig(new MetricDefaultsConfig.Builder()).factory(),
                getMetricDefaultsConfig(cluster).factory());
    }

    @Test
    public void requireThatClusterInfoIsPopulated() {
        ContainerCluster cluster = newContainerCluster();
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
        DeployState state = new DeployState.Builder().properties(new DeployProperties.Builder().hostedVespa(true).build())
                                                     .zone(new Zone(SystemName.cd, Environment.test, RegionName.from("some-region")))
                                                     .build(true);
        MockRoot root = new MockRoot("foo", state);
        ContainerCluster cluster = new ContainerCluster(root, "container0", "container1");
        ConfigserverConfig.Builder builder = new ConfigserverConfig.Builder();
        cluster.getConfig(builder);
        ConfigserverConfig config = new ConfigserverConfig(builder);
        assertEquals(Environment.test.value(), config.environment());
        assertEquals("some-region", config.region());
        assertEquals("cd", config.system());
    }

    private ContainerCluster createContainerCluster(boolean isHosted, boolean isCombinedCluster) {
        return createContainerCluster(isHosted, isCombinedCluster, Optional.empty(), Optional.empty());
    }

    private ContainerCluster createClusterControllerCluster() {
        return createContainerCluster(false, false, new ClusterControllerClusterVerifier());
    }

    private ContainerCluster createContainerCluster(boolean isHosted, boolean isCombinedCluster, ContainerClusterVerifier extraComponents) {
        return createContainerCluster(isHosted, isCombinedCluster, Optional.empty(), Optional.of(extraComponents));
    }

    private ContainerCluster createContainerCluster(boolean isHosted, boolean isCombinedCluster,
                                                    Optional<Integer> memoryPercentage) {
        return createContainerCluster(isHosted, isCombinedCluster, memoryPercentage, Optional.empty());
    }
    private ContainerCluster createContainerCluster(boolean isHosted, boolean isCombinedCluster, 
                                                    Optional<Integer> memoryPercentage, Optional<ContainerClusterVerifier> extraComponents) {
        DeployState state = new DeployState.Builder().properties(new DeployProperties.Builder().hostedVespa(isHosted).build()).build(true);
        MockRoot root = new MockRoot("foo", state);

        ContainerCluster cluster = extraComponents.isPresent()
                ? new ContainerCluster(root, "container0", "container1", extraComponents.get())
                : new ContainerCluster(root, "container0", "container1");
        if (isCombinedCluster)
            cluster.setHostClusterId("test-content-cluster");
        cluster.setMemoryPercentage(memoryPercentage);
        cluster.setSearch(new ContainerSearch(cluster, new SearchChains(cluster, "search-chain"), new ContainerSearch.Options()));
        return cluster;
    }
    private void verifyHeapSizeAsPercentageOfPhysicalMemory(boolean isHosted, boolean isCombinedCluster, 
                                                            Optional<Integer> explicitMemoryPercentage, 
                                                            int expectedMemoryPercentage) {
        ContainerCluster cluster = createContainerCluster(isHosted, isCombinedCluster, explicitMemoryPercentage);
        QrStartConfig.Builder qsB = new QrStartConfig.Builder();
        cluster.getSearch().getConfig(qsB);
        QrStartConfig qsC= new QrStartConfig(qsB);
        assertEquals(expectedMemoryPercentage, qsC.jvm().heapSizeAsPercentageOfPhysicalMemory());
    }

    @Test
    public void requireThatHeapSizeAsPercentageOfPhysicalMemoryForHostedAndNot() {
        boolean hosted = true;
        boolean combined = true; // a cluster running on content nodes (only relevant with hosted)
        verifyHeapSizeAsPercentageOfPhysicalMemory(  hosted, ! combined, Optional.empty(), 60);
        verifyHeapSizeAsPercentageOfPhysicalMemory(  hosted,   combined, Optional.empty(), 17);
        verifyHeapSizeAsPercentageOfPhysicalMemory(! hosted, ! combined, Optional.empty(), 0);
        
        // Explicit value overrides all defaults
        verifyHeapSizeAsPercentageOfPhysicalMemory(  hosted, ! combined, Optional.of(67), 67);
        verifyHeapSizeAsPercentageOfPhysicalMemory(  hosted,   combined, Optional.of(68), 68);
        verifyHeapSizeAsPercentageOfPhysicalMemory(! hosted, ! combined, Optional.of(69), 69);
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
        ContainerCluster cluster = createContainerCluster(isHosted, false);
        if (hasDocProc) {
            cluster.setDocproc(new ContainerDocproc(cluster, null));
        }
        addContainer(cluster, "c1", "host-c1");
        assertEquals(1, cluster.getContainers().size());
        Container container = cluster.getContainers().get(0);
        verifyJvmArgs(isHosted, hasDocProc, "", container.getJvmArgs());
        container.setJvmArgs("initial");
        verifyJvmArgs(isHosted, hasDocProc, "initial", container.getJvmArgs());
        container.prependJvmArgs("ignored");
        verifyJvmArgs(isHosted, hasDocProc, "ignored initial", container.getJvmArgs());
        container.appendJvmArgs("override");
        verifyJvmArgs(isHosted, hasDocProc, "ignored initial override", container.getJvmArgs());
        container.setJvmArgs(null);
        verifyJvmArgs(isHosted, hasDocProc, "", container.getJvmArgs());
    }

    @Test
    public void testContainerClusterMaxThreads() {
        ContainerCluster cluster = createContainerCluster(false, false);
        addContainer(cluster, "c1","host-c1");

        ThreadpoolConfig.Builder tpBuilder = new ThreadpoolConfig.Builder();
        cluster.getConfig(tpBuilder);
        ThreadpoolConfig threadpoolConfig = new ThreadpoolConfig(tpBuilder);
        assertEquals(500, threadpoolConfig.maxthreads());
    }

    @Test
    public void testClusterControllerResourceUsage() {
        ContainerCluster cluster = createClusterControllerCluster();
        addClusterController(cluster, "host-c1");
        assertEquals(1, cluster.getContainers().size());
        ClusterControllerContainer container = (ClusterControllerContainer) cluster.getContainers().get(0);
        QrStartConfig.Builder qrBuilder = new QrStartConfig.Builder();
        container.getConfig(qrBuilder);
        QrStartConfig qrStartConfig = new QrStartConfig(qrBuilder);
        assertEquals(512, qrStartConfig.jvm().heapsize());

        ThreadpoolConfig.Builder tpBuilder = new ThreadpoolConfig.Builder();
        cluster.getConfig(tpBuilder);
        ThreadpoolConfig threadpoolConfig = new ThreadpoolConfig(tpBuilder);
        assertEquals(10, threadpoolConfig.maxthreads());
    }

    @Test
    public void testThatYouCanNotAddNonClusterControllerContainerToClusterControllerCluster() {
        ContainerCluster cluster = createClusterControllerCluster();
        addClusterController(cluster, "host-c1");
        try {
            addContainer(cluster, "c2", "host-c2");
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals("Cluster container1 does not accept container qrserver on host 'host-c2'", e.getMessage());
        }
    }

    @Test
    public void testThatLinguisticsIsExcludedForClusterControllerCluster() {
        ContainerCluster cluster = createClusterControllerCluster();
        addClusterController(cluster, "host-c1");
        assertFalse(contains("com.yahoo.language.provider.SimpleLinguisticsProvider", cluster.getAllComponents()));
    }

    @Test
    public void testThatLinguisticsIsIncludedForNonClusterControllerClusters() {
        ContainerCluster cluster = createContainerCluster(false, false);
        addClusterController(cluster, "host-c1");
        assertTrue(contains("com.yahoo.language.provider.SimpleLinguisticsProvider", cluster.getAllComponents()));
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
        ContainerCluster cluster = createContainerCluster(false, false);
        addContainer(cluster, "c1", "host-c1");
        Container container = cluster.getContainers().get(0);
        container.setJvmArgs("");
        String empty = container.getJvmArgs();
        container.setJvmArgs(null);
        assertEquals(empty, container.getJvmArgs());
    }

    @Test
    public void requireThatRoutingProviderIsDisabledForNonHosted() {
        DeployState state = new DeployState.Builder().properties(new DeployProperties.Builder().hostedVespa(false).build()).build(true);
        MockRoot root = new MockRoot("foo", state);
        ContainerCluster cluster = new ContainerCluster(root, "container0", "container1");
        RoutingProviderConfig.Builder builder = new RoutingProviderConfig.Builder();
        cluster.getConfig(builder);
        RoutingProviderConfig config = new RoutingProviderConfig(builder);
        assertFalse(config.enabled());
        assertEquals(0, cluster.getAllComponents().stream().map(c -> c.getClassId().getName()).filter(c -> c.equals("com.yahoo.jdisc.http.filter.security.RoutingConfigProvider")).count());
    }


    private static void addContainer(ContainerCluster cluster, String name, String hostName) {
        Container container = new Container(cluster, name, 0);
        container.setHostResource(new HostResource(new Host(null, hostName)));
        container.initService();
        cluster.addContainer(container);
    }

    private static void addClusterController(ContainerCluster cluster, String hostName) {
        Container container = new ClusterControllerContainer(cluster, 1, false);
        container.setHostResource(new HostResource(new Host(null, hostName)));
        container.initService();
        cluster.addContainer(container);
    }

    private static ContainerCluster newContainerCluster() {
        ContainerCluster cluster = new ContainerCluster(null, "subId", "name");
        addContainer(cluster, "c1", "host-c1");
        addContainer(cluster, "c2", "host-c2");
        return cluster;
    }

    private static MetricDefaultsConfig getMetricDefaultsConfig(ContainerCluster cluster) {
        MetricDefaultsConfig.Builder builder = new MetricDefaultsConfig.Builder();
        cluster.getConfig(builder);
        return new MetricDefaultsConfig(builder);
    }

    private static ClusterInfoConfig getClusterInfoConfig(ContainerCluster cluster) {
        ClusterInfoConfig.Builder builder = new ClusterInfoConfig.Builder();
        cluster.getConfig(builder);
        return new ClusterInfoConfig(builder);
    }

}

// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.ContainerStatsImpl;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.InetAddressResolver;
import com.yahoo.vespa.hosted.node.admin.util.PathResolver;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;
import org.mockito.InOrder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bakksjo
 */
public class NodeAgentImplTest {
    private static final double MIN_CPU_CORES = 1.0;
    private static final double MIN_MAIN_MEMORY_AVAILABLE_GB = 1.0;
    private static final double MIN_DISK_AVAILABLE_GB = 1.0;
    private static final String vespaVersion = "7.8.9";

    private final String hostName = "host1.test.yahoo.com";
    private final ContainerName containerName = new ContainerName("host1");
    private final DockerOperations dockerOperations = mock(DockerOperations.class);
    private final NodeRepository nodeRepository = mock(NodeRepository.class);
    private final Orchestrator orchestrator = mock(Orchestrator.class);
    private final StorageMaintainer storageMaintainer = mock(StorageMaintainer.class);
    private final MetricReceiverWrapper metricReceiver = new MetricReceiverWrapper(MetricReceiver.nullImplementation);

    private final PathResolver pathResolver = mock(PathResolver.class);
    private final Environment environment = new Environment.Builder()
            .environment("dev")
            .region("us-east-1")
            .parentHostHostname("parent.host.name.yahoo.com")
            .inetAddressResolver(new InetAddressResolver())
            .pathResolver(pathResolver).build();

    private final NodeAgentImpl nodeAgent = new NodeAgentImpl(hostName, nodeRepository, orchestrator, dockerOperations,
            Optional.of(storageMaintainer), metricReceiver, environment);

    @Test
    public void upToDateContainerIsUntouched() throws Exception {
        final long restartGeneration = 1;
        final long rebootGeneration = 0;
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .wantedDockerImage(dockerImage)
                .nodeState(Node.State.active)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .vespaVersion(vespaVersion)
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .wantedRebootGeneration(rebootGeneration)
                .minCpuCores(MIN_CPU_CORES)
                .minMainMemoryAvailableGb(MIN_MAIN_MEMORY_AVAILABLE_GB)
                .minDiskAvailableGb(MIN_DISK_AVAILABLE_GB)
                .build();

        Docker.ContainerStats containerStats = new ContainerStatsImpl(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        when(dockerOperations.getContainer(eq(containerName))).thenReturn(
                Optional.of(new Container(hostName, dockerImage, containerName, Container.State.RUNNING, 1)));
        when(dockerOperations.getContainerStats(any())).thenReturn(Optional.of(containerStats));
        when(dockerOperations.shouldScheduleDownloadOfImage(any())).thenReturn(false);
        when(dockerOperations.startContainerIfNeeded(eq(containerName), eq(nodeSpec))).thenReturn(false);
        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.vespaVersion = nodeSpec.vespaVersion;

        nodeAgent.tick();

        verify(dockerOperations, never()).removeContainer(any(), any());
        verify(orchestrator, never()).suspend(any(String.class));
        verify(dockerOperations, never()).scheduleDownloadOfImage(eq(containerName), any(), any());

        final InOrder inOrder = inOrder(dockerOperations, orchestrator, nodeRepository);
        // TODO: Verify this isn't run unless 1st time
        inOrder.verify(dockerOperations, times(1)).resumeNode(eq(containerName));
        // TODO: This should not happen when nothing is changed. Now it happens 1st time through.
        inOrder.verify(nodeRepository).updateNodeAttributes(
                hostName,
                new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withRebootGeneration(rebootGeneration)
                        .withDockerImage(dockerImage)
                        .withVespaVersion(vespaVersion));
        inOrder.verify(orchestrator).resume(hostName);
    }

    @Test
    public void absentContainerCausesStart() throws Exception {
        final long restartGeneration = 1;
        final long rebootGeneration = 0;
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .wantedDockerImage(dockerImage)
                .nodeState(Node.State.active)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .vespaVersion(vespaVersion)
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .wantedRebootGeneration(rebootGeneration)
                .minCpuCores(MIN_CPU_CORES)
                .minMainMemoryAvailableGb(MIN_MAIN_MEMORY_AVAILABLE_GB)
                .minDiskAvailableGb(MIN_DISK_AVAILABLE_GB)
                .build();

        when(dockerOperations.getContainer(eq(containerName))).thenReturn(Optional.empty());
        when(dockerOperations.getContainerStats(eq(containerName))).thenReturn(Optional.empty());
        when(dockerOperations.shouldScheduleDownloadOfImage(any())).thenReturn(false);
        when(dockerOperations.startContainerIfNeeded(eq(containerName), eq(nodeSpec))).thenReturn(true);
        when(dockerOperations.getVespaVersion(eq(containerName))).thenReturn(Optional.of(vespaVersion));
        when(pathResolver.getApplicationStoragePathForNodeAdmin()).thenReturn(Files.createTempDirectory("foo"));
        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.tick();

        verify(dockerOperations, never()).removeContainer(any(), any());
        verify(orchestrator, never()).suspend(any(String.class));
        verify(dockerOperations, never()).scheduleDownloadOfImage(eq(containerName), any(), any());

        final InOrder inOrder = inOrder(dockerOperations, orchestrator, nodeRepository);
        inOrder.verify(dockerOperations, times(1)).resumeNode(eq(containerName));
        inOrder.verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withRebootGeneration(rebootGeneration)
                        .withDockerImage(dockerImage)
                        .withVespaVersion(vespaVersion));
        inOrder.verify(orchestrator).resume(hostName);
    }

    @Test
    public void containerIsNotStoppedIfNewImageMustBePulled() throws Exception {
        final DockerImage oldDockerImage = new DockerImage("old-image");
        final DockerImage newDockerImage = new DockerImage("new-image");
        final long wantedRestartGeneration = 2;
        final long currentRestartGeneration = 1;
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .wantedDockerImage(newDockerImage)
                .nodeState(Node.State.active)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .wantedRestartGeneration(wantedRestartGeneration)
                .currentRestartGeneration(currentRestartGeneration)
                .minCpuCores(MIN_CPU_CORES)
                .minMainMemoryAvailableGb(MIN_MAIN_MEMORY_AVAILABLE_GB)
                .minDiskAvailableGb(MIN_DISK_AVAILABLE_GB)
                .build();

        when(dockerOperations.shouldScheduleDownloadOfImage(any())).thenReturn(true);
        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));
        when(dockerOperations.getContainer(eq(containerName))).thenReturn(
                Optional.of(new Container(hostName, oldDockerImage, containerName, Container.State.RUNNING, 1)));

        nodeAgent.tick();

        verify(orchestrator, never()).suspend(any(String.class));
        verify(orchestrator, never()).resume(any(String.class));
        verify(dockerOperations, never()).removeContainer(any(), any());

        final InOrder inOrder = inOrder(dockerOperations);
        inOrder.verify(dockerOperations, times(1)).shouldScheduleDownloadOfImage(eq(newDockerImage));
        inOrder.verify(dockerOperations, times(1)).scheduleDownloadOfImage(eq(containerName), eq(nodeSpec), any());
    }

    @Test
    public void noRestartIfOrchestratorSuspendFails() throws Exception {
        final long wantedRestartGeneration = 2;
        final long currentRestartGeneration = 1;
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .wantedDockerImage(dockerImage)
                .nodeState(Node.State.active)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .wantedRestartGeneration(wantedRestartGeneration)
                .currentRestartGeneration(currentRestartGeneration)
                .minCpuCores(MIN_CPU_CORES)
                .minMainMemoryAvailableGb(MIN_MAIN_MEMORY_AVAILABLE_GB)
                .minDiskAvailableGb(MIN_DISK_AVAILABLE_GB)
                .build();

        when(dockerOperations.shouldScheduleDownloadOfImage(any())).thenReturn(false);

        try {
            nodeAgent.tick();
            fail("Expected to throw an exception");
        } catch (Exception ignored) { }

        verify(dockerOperations, never()).startContainerIfNeeded(eq(containerName), eq(nodeSpec));
        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository, never()).updateNodeAttributes(any(String.class), any(NodeAttributes.class));
    }

    @Test
    public void failedNodeRunningContainerShouldStillBeRunning() throws Exception {
        final long restartGeneration = 1;
        final long rebootGeneration = 0;
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .nodeState(Node.State.failed)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .minCpuCores(MIN_CPU_CORES)
                .minMainMemoryAvailableGb(MIN_MAIN_MEMORY_AVAILABLE_GB)
                .minDiskAvailableGb(MIN_DISK_AVAILABLE_GB)
                .build();

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));
        when(dockerOperations.getContainer(eq(containerName))).thenReturn(
                Optional.of(new Container(hostName, dockerImage, containerName, Container.State.RUNNING, 1)));

        nodeAgent.vespaVersion = nodeSpec.vespaVersion;

        nodeAgent.tick();

        verify(dockerOperations, never()).removeContainer(any(), any());
        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withRebootGeneration(rebootGeneration)
                        .withDockerImage(new DockerImage(""))
                        .withVespaVersion(""));
    }

    @Test
    public void readyNodeLeadsToNoAction() throws Exception {
        final long restartGeneration = 1;
        final long rebootGeneration = 0;
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .nodeState(Node.State.ready)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .wantedRebootGeneration(rebootGeneration)
                .minCpuCores(MIN_CPU_CORES)
                .minMainMemoryAvailableGb(MIN_MAIN_MEMORY_AVAILABLE_GB)
                .minDiskAvailableGb(MIN_DISK_AVAILABLE_GB)
                .build();

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));
        when(dockerOperations.getContainer(eq(containerName))).thenReturn(
                Optional.of(new Container(hostName, dockerImage, containerName, Container.State.RUNNING, 1)));

        nodeAgent.vespaVersion = nodeSpec.vespaVersion;

        nodeAgent.tick();

        verify(dockerOperations, never()).removeContainer(any(), any());
        verify(dockerOperations, never()).startContainerIfNeeded(eq(containerName), eq(nodeSpec));
        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withRebootGeneration(rebootGeneration)
                        .withDockerImage(new DockerImage(""))
                        .withVespaVersion(""));
    }

    @Test
    public void inactiveNodeRunningContainerShouldStillBeRunning() throws Exception {
        final long restartGeneration = 1;
        final long rebootGeneration = 0;
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .nodeState(Node.State.inactive)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .wantedRebootGeneration(rebootGeneration)
                .minCpuCores(MIN_CPU_CORES)
                .minMainMemoryAvailableGb(MIN_MAIN_MEMORY_AVAILABLE_GB)
                .minDiskAvailableGb(MIN_DISK_AVAILABLE_GB)
                .build();

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));
        when(dockerOperations.getContainer(eq(containerName))).thenReturn(
                Optional.of(new Container(hostName, dockerImage, containerName, Container.State.RUNNING, 1)));

        nodeAgent.vespaVersion = nodeSpec.vespaVersion;

        nodeAgent.tick();

        final InOrder inOrder = inOrder(storageMaintainer, dockerOperations);
        inOrder.verify(storageMaintainer, times(1)).removeOldFilesFromNode(eq(containerName));
        inOrder.verify(dockerOperations, never()).removeContainer(eq(nodeSpec), any());

        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withRebootGeneration(rebootGeneration)
                        .withDockerImage(new DockerImage(""))
                        .withVespaVersion(""));
    }

    private ContainerNodeSpec makeContainer(Node.State nodeState, Optional<Long> wantedRestartGeneration) {
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerNodeSpec.Builder nodeSpecBuilder = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .wantedDockerImage(dockerImage)
                .nodeState(nodeState)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .minCpuCores(MIN_CPU_CORES)
                .minMainMemoryAvailableGb(MIN_MAIN_MEMORY_AVAILABLE_GB)
                .minDiskAvailableGb(MIN_DISK_AVAILABLE_GB);

        wantedRestartGeneration.ifPresent(restartGeneration -> nodeSpecBuilder
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration));

        final ContainerNodeSpec nodeSpec = nodeSpecBuilder.build();

        boolean shouldBeRunning = nodeState != Node.State.dirty && nodeState != Node.State.provisioned;
        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));
        when(dockerOperations.getContainer(eq(containerName))).thenReturn(
                Optional.of(new Container(hostName,
                        dockerImage,
                        containerName,
                        shouldBeRunning ? Container.State.RUNNING : Container.State.EXITED,
                        shouldBeRunning ? 1 : 0)));
        return nodeSpec;
    }

    private void nodeRunningContainerIsTakenDownAndCleanedAndRecycled(Node.State nodeState, Optional<Long> wantedRestartGeneration)
            throws Exception {
        final ContainerNodeSpec nodeSpec = makeContainer(nodeState, wantedRestartGeneration);

        nodeAgent.tick();

        final InOrder inOrder = inOrder(storageMaintainer, dockerOperations, nodeRepository);
        inOrder.verify(storageMaintainer, times(1)).removeOldFilesFromNode(eq(containerName));
        inOrder.verify(dockerOperations, times(1)).removeContainer(eq(nodeSpec), any());
        inOrder.verify(storageMaintainer, times(1)).archiveNodeData(eq(containerName));
        inOrder.verify(nodeRepository, times(1)).markAsReady(eq(hostName));

        verify(dockerOperations, never()).startContainerIfNeeded(eq(containerName), any());
        verify(orchestrator, never()).resume(any(String.class));
        // current Docker image and vespa version should be cleared
        verify(nodeRepository, times(1)).updateNodeAttributes(
                any(String.class), eq(new NodeAttributes()
                        .withRestartGeneration(wantedRestartGeneration.orElse(null))
                        .withRebootGeneration(0L)
                        .withDockerImage(new DockerImage(""))
                        .withVespaVersion("")));
    }

    @Test
    public void dirtyNodeRunningContainerIsTakenDownAndCleanedAndRecycled() throws Exception {
        nodeRunningContainerIsTakenDownAndCleanedAndRecycled(Node.State.dirty, Optional.of(1L));
    }

    @Test
    public void dirtyNodeRunningContainerIsTakenDownAndCleanedAndRecycledNoRestartGeneration() throws Exception {
        nodeRunningContainerIsTakenDownAndCleanedAndRecycled(Node.State.dirty, Optional.empty());
    }

    @Test
    public void provisionedNodeIsMarkedAsDirty() throws Exception {
        makeContainer(Node.State.provisioned, Optional.empty());
        nodeAgent.tick();
        verify(nodeRepository, times(1)).markAsDirty(eq(hostName));
    }

    @Test
    public void resumeProgramRunsUntilSuccess() throws Exception {
        final long restartGeneration = 1;
        final DockerImage wantedDockerImage = new DockerImage("wantedDockerImage");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .wantedDockerImage(wantedDockerImage)
                .nodeState(Node.State.active)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .minCpuCores(MIN_CPU_CORES)
                .minMainMemoryAvailableGb(MIN_MAIN_MEMORY_AVAILABLE_GB)
                .minDiskAvailableGb(MIN_DISK_AVAILABLE_GB)
                .build();

        Docker.ContainerStats containerStats = new ContainerStatsImpl(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        when(dockerOperations.getContainerStats(any())).thenReturn(Optional.of(containerStats));
        when(dockerOperations.getContainer(eq(containerName))).thenReturn(
                Optional.of(new Container(hostName, wantedDockerImage, containerName, Container.State.RUNNING, 1)));
        when(nodeRepository.getContainerNodeSpec(eq(hostName))).thenReturn(Optional.of(nodeSpec));
        when(dockerOperations.shouldScheduleDownloadOfImage(eq(wantedDockerImage))).thenReturn(false);

        verify(dockerOperations, never()).removeContainer(any(), any());

        doThrow(new RuntimeException("Failed 1st time"))
                .doNothing()
                .when(dockerOperations).resumeNode(eq(containerName));

        final InOrder inOrder = inOrder(orchestrator, dockerOperations, nodeRepository);
        nodeAgent.vespaVersion = nodeSpec.vespaVersion;

        // 1st try
        try {
            nodeAgent.tick();
            fail("Expected to throw an exception");
        } catch (RuntimeException ignored) { }

        inOrder.verify(dockerOperations, times(1)).resumeNode(any());
        inOrder.verifyNoMoreInteractions();

        // 2nd try
        nodeAgent.tick();

        inOrder.verify(dockerOperations).resumeNode(any());
        inOrder.verify(orchestrator).resume(hostName);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetRelevantMetrics() throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();
        ClassLoader classLoader = getClass().getClassLoader();
        File statsFile = new File(classLoader.getResource("docker.stats.json").getFile());
        Map<String, Object> dockerStats = objectMapper.readValue(statsFile, Map.class);

        Map<String, Object> networks = (Map<String, Object>) dockerStats.get("networks");
        Map<String, Object> cpu_stats = (Map<String, Object>) dockerStats.get("cpu_stats");
        Map<String, Object> memory_stats = (Map<String, Object>) dockerStats.get("memory_stats");
        Map<String, Object> blkio_stats = (Map<String, Object>) dockerStats.get("blkio_stats");
        Docker.ContainerStats stats = new ContainerStatsImpl(networks, cpu_stats, memory_stats, blkio_stats);

        when(dockerOperations.getContainerStats(eq(containerName))).thenReturn(Optional.of(stats));

        when(dockerOperations.getContainer(eq(containerName))).thenReturn(
                Optional.of(new Container(hostName, new DockerImage("wantedDockerImage"), containerName, Container.State.RUNNING, 1)));

        nodeAgent.vespaVersion = Optional.of("1.2.3");
        ContainerNodeSpec.Owner owner = new ContainerNodeSpec.Owner("tester", "testapp", "testinstance");
        ContainerNodeSpec.Membership membership = new ContainerNodeSpec.Membership("clustType", "clustId", "grp", 3, false);
        nodeAgent.lastNodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .nodeState(Node.State.active)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .owner(owner)
                .membership(membership)
                .minDiskAvailableGb(250)
                .build();

        long totalContainerCpuTime = (long) ((Map) cpu_stats.get("cpu_usage")).get("total_usage");
        long totalSystemCpuTime = (long) cpu_stats.get("system_cpu_usage");
        nodeAgent.lastCpuMetric.getCpuUsagePercentage(totalContainerCpuTime - 456_789_123, (long) (totalSystemCpuTime - 1e9));
        // During the last 10^9 total cpu ns, 456,789,123ns were spent on running the container. That means the expected
        // cpu usage percentage is 100 * (456,789,123 / 10^9) = 45.6789123%
        nodeAgent.updateContainerNodeMetrics();

        Set<Map<String, Object>> actualMetrics = new HashSet<>();
        for (MetricReceiverWrapper.DimensionMetrics dimensionMetrics : metricReceiver.getMetrics(MetricReceiverWrapper.APPLICATION_DOCKER)) {
            Map<String, Object> metrics = objectMapper.readValue(dimensionMetrics.toSecretAgentReport(), Map.class);
            metrics.remove("timestamp"); // Remove timestamp so we can test against expected map
            actualMetrics.add(metrics);
        }

        for (MetricReceiverWrapper.DimensionMetrics dimensionMetrics : metricReceiver.getMetrics(MetricReceiverWrapper.APPLICATION_HOST_LIFE)) {
            Map<String, Object> metrics = objectMapper.readValue(dimensionMetrics.toSecretAgentReport(), Map.class);
            metrics.remove("timestamp"); // Remove timestamp so we can test against expected map
            ((Map)metrics.get("metrics")).remove("uptime"); // Remove uptime so we can test against expected map. Implicit test of field existing too
            actualMetrics.add(metrics);
        }

        File expectedMetricsFile = new File(classLoader.getResource("docker.stats.metrics.expected.json").getFile());
        Set<Map<String, Object>> expectedMetrics = objectMapper.readValue(expectedMetricsFile, Set.class);

        assertEquals(expectedMetrics, actualMetrics);
    }

}

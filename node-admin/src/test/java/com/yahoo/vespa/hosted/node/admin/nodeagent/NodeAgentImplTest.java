// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.ContainerResources;
import com.yahoo.vespa.hosted.dockerapi.ContainerStatsImpl;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.util.InetAddressResolver;
import com.yahoo.vespa.hosted.node.admin.component.PathResolver;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;
import org.mockito.InOrder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bakksjo
 */
public class NodeAgentImplTest {
    private static final Duration NODE_AGENT_SCAN_INTERVAL = Duration.ofSeconds(30);
    private static final double MIN_CPU_CORES = 2;
    private static final double MIN_MAIN_MEMORY_AVAILABLE_GB = 16;
    private static final double MIN_DISK_AVAILABLE_GB = 250;
    private static final String vespaVersion = "1.2.3";

    private final String hostName = "host1.test.yahoo.com";
    private final ContainerName containerName = new ContainerName("host1");
    private final DockerImage dockerImage = new DockerImage("dockerImage");
    private final DockerOperations dockerOperations = mock(DockerOperations.class);
    private final NodeRepository nodeRepository = mock(NodeRepository.class);
    private final Orchestrator orchestrator = mock(Orchestrator.class);
    private final StorageMaintainer storageMaintainer = mock(StorageMaintainer.class);
    private final MetricReceiverWrapper metricReceiver = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
    private final AclMaintainer aclMaintainer = mock(AclMaintainer.class);
    private final Docker.ContainerStats emptyContainerStats = new ContainerStatsImpl(Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

    private final PathResolver pathResolver = mock(PathResolver.class);
    private final ManualClock clock = new ManualClock();
    private final Environment environment = new Environment.Builder()
            .environment("dev")
            .region("us-east-1")
            .parentHostHostname("parent.host.name.yahoo.com")
            .inetAddressResolver(new InetAddressResolver())
            .pathResolver(pathResolver).build();

    private final ContainerNodeSpec.Builder nodeSpecBuilder = new ContainerNodeSpec.Builder()
            .hostname(hostName)
            .nodeType("tenant")
            .nodeFlavor("docker")
            .minCpuCores(MIN_CPU_CORES)
            .minMainMemoryAvailableGb(MIN_MAIN_MEMORY_AVAILABLE_GB)
            .minDiskAvailableGb(MIN_DISK_AVAILABLE_GB);


    @Test
    public void upToDateContainerIsUntouched() {
        final long restartGeneration = 1;
        final long rebootGeneration = 0;
        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .wantedDockerImage(dockerImage)
                .currentDockerImage(dockerImage)
                .nodeState(Node.State.active)
                .wantedVespaVersion(vespaVersion)
                .vespaVersion(vespaVersion)
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .wantedRebootGeneration(rebootGeneration)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);
        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));
        when(storageMaintainer.getDiskUsageFor(eq(containerName))).thenReturn(Optional.of(187500000000L));

        nodeAgent.converge();

        verify(dockerOperations, never()).removeContainer(any(), any());
        verify(orchestrator, never()).suspend(any(String.class));
        verify(dockerOperations, never()).pullImageAsyncIfNeeded(any());
        verify(storageMaintainer, never()).removeOldFilesFromNode(eq(containerName));

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
    public void verifyRemoveOldFilesIfDiskFull() {
        final long restartGeneration = 1;
        final long rebootGeneration = 0;
        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .wantedDockerImage(dockerImage)
                .currentDockerImage(dockerImage)
                .nodeState(Node.State.active)
                .wantedVespaVersion(vespaVersion)
                .vespaVersion(vespaVersion)
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .wantedRebootGeneration(rebootGeneration)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);
        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));
        when(storageMaintainer.getDiskUsageFor(eq(containerName))).thenReturn(Optional.of(217432719360L));

        nodeAgent.converge();

        verify(storageMaintainer, times(1)).removeOldFilesFromNode(eq(containerName));
    }


    @Test
    public void absentContainerCausesStart() throws Exception {
        final long restartGeneration = 1;
        final long rebootGeneration = 0;
        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .wantedDockerImage(dockerImage)
                .nodeState(Node.State.active)
                .wantedVespaVersion(vespaVersion)
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .wantedRebootGeneration(rebootGeneration)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(null, false);

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));
        when(pathResolver.getApplicationStoragePathForNodeAdmin()).thenReturn(Files.createTempDirectory("foo"));
        when(dockerOperations.pullImageAsyncIfNeeded(eq(dockerImage))).thenReturn(false);
        when(storageMaintainer.getDiskUsageFor(eq(containerName))).thenReturn(Optional.of(201326592000L));

        nodeAgent.converge();

        verify(dockerOperations, never()).removeContainer(any(), any());
        verify(orchestrator, never()).suspend(any(String.class));

        final InOrder inOrder = inOrder(dockerOperations, orchestrator, nodeRepository, aclMaintainer);
        inOrder.verify(dockerOperations, times(1)).pullImageAsyncIfNeeded(eq(dockerImage));
        inOrder.verify(aclMaintainer, times(1)).run();
        inOrder.verify(dockerOperations, times(1)).startContainer(eq(containerName), eq(nodeSpec));
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
    public void containerIsNotStoppedIfNewImageMustBePulled() {
        final DockerImage newDockerImage = new DockerImage("new-image");
        final long wantedRestartGeneration = 2;
        final long currentRestartGeneration = 1;
        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .wantedDockerImage(newDockerImage)
                .currentDockerImage(dockerImage)
                .nodeState(Node.State.active)
                .wantedVespaVersion(vespaVersion)
                .vespaVersion(vespaVersion)
                .wantedRestartGeneration(wantedRestartGeneration)
                .currentRestartGeneration(currentRestartGeneration)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));
        when(dockerOperations.pullImageAsyncIfNeeded(any())).thenReturn(true);
        when(storageMaintainer.getDiskUsageFor(eq(containerName))).thenReturn(Optional.of(201326592000L));

        nodeAgent.converge();

        verify(orchestrator, never()).suspend(any(String.class));
        verify(orchestrator, never()).resume(any(String.class));
        verify(dockerOperations, never()).removeContainer(any(), any());

        final InOrder inOrder = inOrder(dockerOperations);
        inOrder.verify(dockerOperations, times(1)).pullImageAsyncIfNeeded(eq(newDockerImage));
    }

    @Test
    public void containerIsRestartedIfFlavorChanged() {
        final long wantedRestartGeneration = 1;
        final long currentRestartGeneration = 1;
        ContainerNodeSpec.Builder specBuilder = nodeSpecBuilder
                .wantedDockerImage(dockerImage)
                .currentDockerImage(dockerImage)
                .nodeState(Node.State.active)
                .wantedVespaVersion(vespaVersion)
                .vespaVersion(vespaVersion)
                .wantedRestartGeneration(wantedRestartGeneration)
                .currentRestartGeneration(currentRestartGeneration);

        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);
        ContainerNodeSpec firstSpec = specBuilder.build();
        ContainerNodeSpec secondSpec = specBuilder.minDiskAvailableGb(200).build();
        ContainerNodeSpec thirdSpec = specBuilder.minCpuCores(4).build();

        when(nodeRepository.getContainerNodeSpec(hostName))
                .thenReturn(Optional.of(firstSpec))
                .thenReturn(Optional.of(secondSpec))
                .thenReturn(Optional.of(thirdSpec));
        when(dockerOperations.pullImageAsyncIfNeeded(any())).thenReturn(true);
        when(storageMaintainer.getDiskUsageFor(eq(containerName))).thenReturn(Optional.of(201326592000L));

        nodeAgent.converge();
        nodeAgent.converge();
        nodeAgent.converge();

        InOrder inOrder = inOrder(orchestrator, dockerOperations);
        inOrder.verify(orchestrator).resume(any(String.class));
        inOrder.verify(orchestrator).resume(any(String.class));
        inOrder.verify(orchestrator).suspend(any(String.class));
        inOrder.verify(dockerOperations).removeContainer(any(), any());
        inOrder.verify(dockerOperations).startContainer(eq(containerName), eq(thirdSpec));
        inOrder.verify(orchestrator).resume(any(String.class));
    }

    @Test
    public void noRestartIfOrchestratorSuspendFails() {
        final long wantedRestartGeneration = 2;
        final long currentRestartGeneration = 1;
        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .wantedDockerImage(dockerImage)
                .currentDockerImage(dockerImage)
                .nodeState(Node.State.active)
                .wantedVespaVersion(vespaVersion)
                .vespaVersion(vespaVersion)
                .wantedRestartGeneration(wantedRestartGeneration)
                .currentRestartGeneration(currentRestartGeneration)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        try {
            nodeAgent.converge();
            fail("Expected to throw an exception");
        } catch (Exception ignored) { }

        verify(dockerOperations, never()).startContainer(eq(containerName), eq(nodeSpec));
        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository, never()).updateNodeAttributes(any(String.class), any(NodeAttributes.class));
    }

    @Test
    public void failedNodeRunningContainerShouldStillBeRunning() {
        final long restartGeneration = 1;
        final long rebootGeneration = 0;
        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .wantedDockerImage(dockerImage)
                .currentDockerImage(dockerImage)
                .nodeState(Node.State.failed)
                .wantedVespaVersion(vespaVersion)
                .vespaVersion(vespaVersion)
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.converge();

        verify(dockerOperations, never()).removeContainer(any(), any());
        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withRebootGeneration(rebootGeneration)
                        .withDockerImage(dockerImage)
                        .withVespaVersion(vespaVersion));
    }

    @Test
    public void readyNodeLeadsToNoAction() {
        final long restartGeneration = 1;
        final long rebootGeneration = 0;
        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .nodeState(Node.State.ready)
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .wantedRebootGeneration(rebootGeneration)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(null,false);

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.converge();
        nodeAgent.converge();
        nodeAgent.converge();

        // Should only be called once, when we initialize
        verify(dockerOperations, times(1)).getContainer(eq(containerName));
        verify(dockerOperations, never()).removeContainer(any(), any());
        verify(dockerOperations, never()).startContainer(eq(containerName), eq(nodeSpec));
        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withRebootGeneration(rebootGeneration)
                        .withDockerImage(new DockerImage(""))
                        .withVespaVersion(""));
    }

    @Test
    public void inactiveNodeRunningContainerShouldStillBeRunning() {
        final long restartGeneration = 1;
        final long rebootGeneration = 0;

        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .wantedDockerImage(dockerImage)
                .currentDockerImage(dockerImage)
                .nodeState(Node.State.inactive)
                .wantedVespaVersion(vespaVersion)
                .vespaVersion(vespaVersion)
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .wantedRebootGeneration(rebootGeneration)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.converge();

        final InOrder inOrder = inOrder(storageMaintainer, dockerOperations);
        inOrder.verify(dockerOperations, never()).removeContainer(any(), any());

        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withRebootGeneration(rebootGeneration)
                        .withDockerImage(dockerImage)
                        .withVespaVersion(vespaVersion));
    }

    @Test
    public void reservedNodeDoesNotUpdateNodeRepoWithVersion() {
        final long restartGeneration = 1;
        final long rebootGeneration = 0;

        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .wantedDockerImage(dockerImage)
                .nodeState(Node.State.reserved)
                .wantedVespaVersion(vespaVersion)
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .wantedRebootGeneration(rebootGeneration)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(null, false);

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.converge();

        verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withRebootGeneration(rebootGeneration)
                        .withDockerImage(new DockerImage(""))
                        .withVespaVersion(""));
    }

    private void nodeRunningContainerIsTakenDownAndCleanedAndRecycled(Node.State nodeState, Optional<Long> wantedRestartGeneration) {
        wantedRestartGeneration.ifPresent(restartGeneration -> nodeSpecBuilder
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration));

        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .wantedDockerImage(dockerImage)
                .currentDockerImage(dockerImage)
                .nodeState(nodeState)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.converge();

        final InOrder inOrder = inOrder(storageMaintainer, dockerOperations, nodeRepository);
        inOrder.verify(dockerOperations, times(1)).removeContainer(any(), any());
        inOrder.verify(storageMaintainer, times(1)).cleanupNodeStorage(eq(containerName), eq(nodeSpec));
        inOrder.verify(nodeRepository, times(1)).markNodeAvailableForNewAllocation(eq(hostName));

        verify(dockerOperations, never()).startContainer(eq(containerName), any());
        verify(orchestrator, never()).resume(any(String.class));
        verify(orchestrator, never()).suspend(any(String.class));
        // current Docker image and vespa version should be cleared
        verify(nodeRepository, times(1)).updateNodeAttributes(
                any(String.class), eq(new NodeAttributes()
                        .withRestartGeneration(wantedRestartGeneration.orElse(null))
                        .withRebootGeneration(0L)
                        .withDockerImage(new DockerImage(""))
                        .withVespaVersion("")));
    }

    @Test
    public void dirtyNodeRunningContainerIsTakenDownAndCleanedAndRecycled() {
        nodeRunningContainerIsTakenDownAndCleanedAndRecycled(Node.State.dirty, Optional.of(1L));
    }

    @Test
    public void dirtyNodeRunningContainerIsTakenDownAndCleanedAndRecycledNoRestartGeneration() {
        nodeRunningContainerIsTakenDownAndCleanedAndRecycled(Node.State.dirty, Optional.empty());
    }

    @Test
    public void provisionedNodeIsMarkedAsDirty() {
        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .wantedDockerImage(dockerImage)
                .nodeState(Node.State.provisioned)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(null, false);
        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.converge();
        verify(nodeRepository, times(1)).markAsDirty(eq(hostName));
    }

    @Test
    public void testRestartDeadContainerAfterNodeAdminRestart() throws IOException {
        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .currentDockerImage(dockerImage)
                .wantedDockerImage(dockerImage)
                .nodeState(Node.State.active)
                .vespaVersion(vespaVersion)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, false);

        when(nodeRepository.getContainerNodeSpec(eq(hostName))).thenReturn(Optional.of(nodeSpec));
        when(pathResolver.getApplicationStoragePathForNodeAdmin()).thenReturn(Files.createTempDirectory("foo"));
        when(storageMaintainer.getDiskUsageFor(eq(containerName))).thenReturn(Optional.of(201326592000L));

        nodeAgent.tick();

        verify(dockerOperations, times(1)).removeContainer(any(), any());
        verify(dockerOperations, times(1)).startContainer(eq(containerName), eq(nodeSpec));
    }

    @Test
    public void resumeProgramRunsUntilSuccess() {
        final long restartGeneration = 1;
        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .wantedDockerImage(dockerImage)
                .currentDockerImage(dockerImage)
                .nodeState(Node.State.active)
                .vespaVersion(vespaVersion)
                .wantedRestartGeneration(restartGeneration)
                .currentRestartGeneration(restartGeneration)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getContainerNodeSpec(eq(hostName))).thenReturn(Optional.of(nodeSpec));
        when(storageMaintainer.getDiskUsageFor(eq(containerName))).thenReturn(Optional.of(201326592000L));

        final InOrder inOrder = inOrder(orchestrator, dockerOperations, nodeRepository);
        doThrow(new RuntimeException("Failed 1st time"))
                .doNothing()
                .when(dockerOperations).resumeNode(eq(containerName));

        // 1st try
        try {
            nodeAgent.converge();
            fail("Expected to throw an exception");
        } catch (RuntimeException ignored) { }

        inOrder.verify(dockerOperations, times(1)).resumeNode(any());
        inOrder.verifyNoMoreInteractions();

        // 2nd try
        nodeAgent.converge();

        inOrder.verify(dockerOperations).resumeNode(any());
        inOrder.verify(orchestrator).resume(hostName);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSetFrozen() {
        NodeAgentImpl nodeAgent = spy(makeNodeAgent(dockerImage, true));
        doNothing().when(nodeAgent).converge();

        nodeAgent.tick();
        verify(nodeAgent, times(1)).converge();

        assertFalse(nodeAgent.setFrozen(true)); // Returns true because we are not frozen until tick is called
        nodeAgent.tick();
        verify(nodeAgent, times(1)).converge(); // Frozen should be set, therefore converge is never called

        assertTrue(nodeAgent.setFrozen(true)); // Attempt to set frozen again, but it's already set
        clock.advance(Duration.ofSeconds(35)); // workToDoNow is no longer set, so we need to wait the regular time
        nodeAgent.tick();
        verify(nodeAgent, times(1)).converge();

        assertFalse(nodeAgent.setFrozen(false)); // Unfreeze, but still need to call tick for it to take effect
        nodeAgent.tick();
        verify(nodeAgent, times(2)).converge();

        assertTrue(nodeAgent.setFrozen(false));
        clock.advance(Duration.ofSeconds(35)); // workToDoNow is no longer set, so we need to wait the regular time
        nodeAgent.tick();
        verify(nodeAgent, times(3)).converge();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetRelevantMetrics() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();
        ClassLoader classLoader = getClass().getClassLoader();
        File statsFile = new File(classLoader.getResource("docker.stats.json").getFile());
        Map<String, Map<String, Object>> dockerStats = objectMapper.readValue(statsFile, Map.class);

        Map<String, Object> networks = dockerStats.get("networks");
        Map<String, Object> precpu_stats = dockerStats.get("precpu_stats");
        Map<String, Object> cpu_stats = dockerStats.get("cpu_stats");
        Map<String, Object> memory_stats = dockerStats.get("memory_stats");
        Map<String, Object> blkio_stats = dockerStats.get("blkio_stats");
        Docker.ContainerStats stats1 = new ContainerStatsImpl(networks, precpu_stats, memory_stats, blkio_stats);
        Docker.ContainerStats stats2 = new ContainerStatsImpl(networks, cpu_stats, memory_stats, blkio_stats);

        ContainerNodeSpec.Owner owner = new ContainerNodeSpec.Owner("tester", "testapp", "testinstance");
        ContainerNodeSpec.Membership membership = new ContainerNodeSpec.Membership("clustType", "clustId", "grp", 3, false);
        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .wantedDockerImage(dockerImage)
                .currentDockerImage(dockerImage)
                .nodeState(Node.State.active)
                .vespaVersion(vespaVersion)
                .owner(owner)
                .membership(membership)
                .minMainMemoryAvailableGb(2)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getContainerNodeSpec(eq(hostName))).thenReturn(Optional.of(nodeSpec));
        when(storageMaintainer.getDiskUsageFor(eq(containerName))).thenReturn(Optional.of(39625000000L));
        when(dockerOperations.getContainerStats(eq(containerName)))
                .thenReturn(Optional.of(stats1))
                .thenReturn(Optional.of(stats2));

        nodeAgent.converge(); // Run the converge loop once to initialize lastNodeSpec
        nodeAgent.updateContainerNodeMetrics(); // Update metrics once to init and lastCpuMetric

        clock.advance(Duration.ofSeconds(1234));

        Path pathToExpectedMetrics = Paths.get(classLoader.getResource("expected.container.system.metrics.txt").getPath());
        String expectedMetrics = new String(Files.readAllBytes(pathToExpectedMetrics))
                .replaceAll("\\s", "")
                .replaceAll("\\n", "");

        String[] expectedCommand = {"vespa-rpc-invoke",  "-t", "2",  "tcp/localhost:19091",  "setExtraMetrics", expectedMetrics};
        doAnswer(invocation -> {
            ContainerName calledContainerName = (ContainerName) invocation.getArguments()[0];
            long calledTimeout = (long) invocation.getArguments()[1];
            String[] calledCommand = new String[invocation.getArguments().length - 2];
            System.arraycopy(invocation.getArguments(), 2, calledCommand, 0, calledCommand.length);
            calledCommand[calledCommand.length - 1] = calledCommand[calledCommand.length - 1].replaceAll("\"timestamp\":\\d+", "\"timestamp\":0");

            assertEquals(containerName, calledContainerName);
            assertEquals(5L, calledTimeout);
            assertArrayEquals(expectedCommand, calledCommand);
            return null;
        }).when(dockerOperations).executeCommandInContainerAsRoot(any(), any(), anyVararg());

        nodeAgent.updateContainerNodeMetrics();
    }

    @Test
    public void testGetRelevantMetricsForReadyNode() {
        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .nodeState(Node.State.ready)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(null, false);

        when(nodeRepository.getContainerNodeSpec(eq(hostName))).thenReturn(Optional.of(nodeSpec));
        when(dockerOperations.getContainerStats(eq(containerName))).thenReturn(Optional.empty());

        nodeAgent.converge(); // Run the converge loop once to initialize lastNodeSpec

        nodeAgent.updateContainerNodeMetrics();

        Set<Map<String, Object>> actualMetrics = metricReceiver.getAllMetricsRaw();
        assertEquals(Collections.emptySet(), actualMetrics);
    }


    private NodeAgentImpl makeNodeAgent(DockerImage dockerImage, boolean isRunning) {
        Optional<Container> container = dockerImage != null ?
                Optional.of(new Container(
                        hostName,
                        dockerImage,
                        ContainerResources.from(MIN_CPU_CORES, MIN_MAIN_MEMORY_AVAILABLE_GB),
                        containerName,
                        isRunning ? Container.State.RUNNING : Container.State.EXITED,
                        isRunning ? 1 : 0)) :
                Optional.empty();

        when(dockerOperations.getContainerStats(any())).thenReturn(Optional.of(emptyContainerStats));
        when(dockerOperations.getContainer(eq(containerName))).thenReturn(container);
        doNothing().when(storageMaintainer).writeFilebeatConfig(any(), any());
        doNothing().when(storageMaintainer).writeMetricsConfig(any(), any());

        return new NodeAgentImpl(hostName, nodeRepository, orchestrator, dockerOperations,
                storageMaintainer, aclMaintainer, environment, clock, NODE_AGENT_SCAN_INTERVAL);
    }
}

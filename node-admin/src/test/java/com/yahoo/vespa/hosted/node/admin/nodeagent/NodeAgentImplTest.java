// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.ContainerStatsImpl;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
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
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
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
    public void upToDateContainerIsUntouched() throws Exception {
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

        nodeAgent.converge();

        verify(dockerOperations, never()).removeContainer(any());
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

        nodeAgent.converge();

        verify(dockerOperations, never()).removeContainer(any());
        verify(orchestrator, never()).suspend(any(String.class));
        verify(dockerOperations, never()).scheduleDownloadOfImage(eq(containerName), any(), any());

        final InOrder inOrder = inOrder(dockerOperations, orchestrator, nodeRepository, aclMaintainer);
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
    public void containerIsNotStoppedIfNewImageMustBePulled() throws Exception {
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
        when(dockerOperations.shouldScheduleDownloadOfImage(any())).thenReturn(true);

        nodeAgent.converge();

        verify(orchestrator, never()).suspend(any(String.class));
        verify(orchestrator, never()).resume(any(String.class));
        verify(dockerOperations, never()).removeContainer(any());

        final InOrder inOrder = inOrder(dockerOperations);
        inOrder.verify(dockerOperations, times(1)).shouldScheduleDownloadOfImage(eq(newDockerImage));
        inOrder.verify(dockerOperations, times(1)).scheduleDownloadOfImage(eq(containerName), eq(newDockerImage), any());
    }

    @Test
    public void noRestartIfOrchestratorSuspendFails() throws Exception {
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
    public void failedNodeRunningContainerShouldStillBeRunning() throws Exception {
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

        verify(dockerOperations, never()).removeContainer(any());
        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withRebootGeneration(rebootGeneration)
                        .withDockerImage(dockerImage)
                        .withVespaVersion(vespaVersion));
    }

    @Test
    public void readyNodeLeadsToNoAction() throws Exception {
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
        verify(dockerOperations, never()).removeContainer(any());
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
    public void inactiveNodeRunningContainerShouldStillBeRunning() throws Exception {
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
        inOrder.verify(storageMaintainer, times(1)).removeOldFilesFromNode(eq(containerName));
        inOrder.verify(dockerOperations, never()).removeContainer(any());

        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withRebootGeneration(rebootGeneration)
                        .withDockerImage(dockerImage)
                        .withVespaVersion(vespaVersion));
    }

    @Test
    public void reservedNodeDoesNotUpdateNodeRepoWithVersion() throws Exception {
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
        inOrder.verify(storageMaintainer, times(1)).removeOldFilesFromNode(eq(containerName));
        inOrder.verify(dockerOperations, times(1)).removeContainer(any());
        inOrder.verify(storageMaintainer, times(1)).archiveNodeData(eq(containerName));
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
    public void provisionedNodeIsMarkedAsDirty() throws Exception {
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

        nodeAgent.tick();

        verify(dockerOperations, times(1)).removeContainer(any());
        verify(dockerOperations, times(1)).startContainer(eq(containerName), eq(nodeSpec));
    }

    @Test
    public void resumeProgramRunsUntilSuccess() throws Exception {
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
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getContainerNodeSpec(eq(hostName))).thenReturn(Optional.of(nodeSpec));
        when(storageMaintainer.updateIfNeededAndGetDiskMetricsFor(eq(containerName))).thenReturn(Optional.of(42547019776L));
        when(dockerOperations.getContainerStats(eq(containerName)))
                .thenReturn(Optional.of(stats1))
                .thenReturn(Optional.of(stats2));

        nodeAgent.converge(); // Run the converge loop once to initialize lastNodeSpec
        nodeAgent.updateContainerNodeMetrics(5); // Update metrics once to init and lastCpuMetric

        clock.advance(Duration.ofSeconds(1234));
        nodeAgent.updateContainerNodeMetrics(5);


        File expectedMetricsFile = new File(classLoader.getResource("docker.stats.metrics.active.expected.json").getFile());
        Set<Map<String, Object>> expectedMetrics = objectMapper.readValue(expectedMetricsFile, Set.class);
        Set<Map<String, Object>> actualMetrics = metricReceiver.getAllMetricsRaw();

        String arg = nodeAgent.buildRPCArgumentFromMetrics();
        arg = arg.replaceAll("\"timestamp\":\\d+", "\"timestamp\":0");

        assertEquals("s:'{\"routing\":{\"yamas\":{\"namespaces\":[\"Vespa\"]}},\"application\":\"vespa.node\",\"metrics\":{\"mem.limit\":4.294967296E9,\"mem.used\":1.073741824E9,\"alive\":1.0,\"disk.used\":4.2547019776E10,\"disk.util\":15.85,\"cpu.util\":6.75,\"disk.limit\":2.68435456E11,\"mem.util\":25.0},\"dimensions\":{\"app\":\"testapp.testinstance\",\"role\":\"tenants\",\"instanceName\":\"testinstance\",\"vespaVersion\":\"1.2.3\",\"clusterid\":\"clustId\",\"parentHostname\":\"parent.host.name.yahoo.com\",\"flavor\":\"docker\",\"clustertype\":\"clustType\",\"tenantName\":\"tester\",\"zone\":\"dev.us-east-1\",\"host\":\"host1.test.yahoo.com\",\"state\":\"active\",\"applicationId\":\"tester.testapp.testinstance\",\"applicationName\":\"testapp\"},\"timestamp\":0}{\"routing\":{\"yamas\":{\"namespaces\":[\"Vespa\"]}},\"application\":\"vespa.node\",\"metrics\":{\"net.out.bytes\":2.0303455E7,\"net.out.dropped\":13.0,\"net.in.dropped\":4.0,\"net.in.bytes\":1.949927E7,\"net.out.errors\":3.0,\"net.in.errors\":55.0},\"dimensions\":{\"app\":\"testapp.testinstance\",\"role\":\"tenants\",\"instanceName\":\"testinstance\",\"vespaVersion\":\"1.2.3\",\"clusterid\":\"clustId\",\"interface\":\"eth0\",\"parentHostname\":\"parent.host.name.yahoo.com\",\"flavor\":\"docker\",\"clustertype\":\"clustType\",\"tenantName\":\"tester\",\"zone\":\"dev.us-east-1\",\"host\":\"host1.test.yahoo.com\",\"state\":\"active\",\"applicationId\":\"tester.testapp.testinstance\",\"applicationName\":\"testapp\"},\"timestamp\":0}{\"routing\":{\"yamas\":{\"namespaces\":[\"Vespa\"]}},\"application\":\"vespa.node\",\"metrics\":{\"net.out.bytes\":5.4246745E7,\"net.out.dropped\":0.0,\"net.in.dropped\":0.0,\"net.in.bytes\":3245766.0,\"net.out.errors\":0.0,\"net.in.errors\":0.0},\"dimensions\":{\"app\":\"testapp.testinstance\",\"role\":\"tenants\",\"instanceName\":\"testinstance\",\"vespaVersion\":\"1.2.3\",\"clusterid\":\"clustId\",\"interface\":\"eth1\",\"parentHostname\":\"parent.host.name.yahoo.com\",\"flavor\":\"docker\",\"clustertype\":\"clustType\",\"tenantName\":\"tester\",\"zone\":\"dev.us-east-1\",\"host\":\"host1.test.yahoo.com\",\"state\":\"active\",\"applicationId\":\"tester.testapp.testinstance\",\"applicationName\":\"testapp\"},\"timestamp\":0}{\"routing\":{\"yamas\":{\"namespaces\":[\"Vespa\"]}},\"application\":\"host_life\",\"metrics\":{\"alive\":1.0,\"uptime\":1234.0},\"dimensions\":{\"app\":\"testapp.testinstance\",\"role\":\"tenants\",\"instanceName\":\"testinstance\",\"vespaVersion\":\"1.2.3\",\"clusterid\":\"clustId\",\"parentHostname\":\"parent.host.name.yahoo.com\",\"flavor\":\"docker\",\"clustertype\":\"clustType\",\"tenantName\":\"tester\",\"zone\":\"dev.us-east-1\",\"host\":\"host1.test.yahoo.com\",\"state\":\"active\",\"applicationId\":\"tester.testapp.testinstance\",\"applicationName\":\"testapp\"},\"timestamp\":0}{\"routing\":{\"yamas\":{\"namespaces\":[\"Vespa\"]}},\"application\":\"docker\",\"metrics\":{\"node.disk.limit\":2.68435456E11,\"node.disk.used\":4.2547019776E10,\"node.memory.usage\":1.073741824E9,\"node.cpu.busy.pct\":6.75,\"node.cpu.throttled_time\":4523.0,\"node.memory.limit\":4.294967296E9,\"node.alive\":1.0},\"dimensions\":{\"app\":\"testapp.testinstance\",\"role\":\"tenants\",\"instanceName\":\"testinstance\",\"vespaVersion\":\"1.2.3\",\"clusterid\":\"clustId\",\"parentHostname\":\"parent.host.name.yahoo.com\",\"flavor\":\"docker\",\"clustertype\":\"clustType\",\"tenantName\":\"tester\",\"zone\":\"dev.us-east-1\",\"host\":\"host1.test.yahoo.com\",\"state\":\"active\",\"applicationId\":\"tester.testapp.testinstance\",\"applicationName\":\"testapp\"},\"timestamp\":0}{\"routing\":{\"yamas\":{\"namespaces\":[\"Vespa\"]}},\"application\":\"docker\",\"metrics\":{\"node.net.in.dropped\":4.0,\"node.net.out.errors\":3.0,\"node.net.out.bytes\":2.0303455E7,\"node.net.in.bytes\":1.949927E7,\"node.net.out.dropped\":13.0,\"node.net.in.errors\":55.0},\"dimensions\":{\"app\":\"testapp.testinstance\",\"role\":\"tenants\",\"instanceName\":\"testinstance\",\"vespaVersion\":\"1.2.3\",\"clusterid\":\"clustId\",\"interface\":\"eth0\",\"parentHostname\":\"parent.host.name.yahoo.com\",\"flavor\":\"docker\",\"clustertype\":\"clustType\",\"tenantName\":\"tester\",\"zone\":\"dev.us-east-1\",\"host\":\"host1.test.yahoo.com\",\"state\":\"active\",\"applicationId\":\"tester.testapp.testinstance\",\"applicationName\":\"testapp\"},\"timestamp\":0}{\"routing\":{\"yamas\":{\"namespaces\":[\"Vespa\"]}},\"application\":\"docker\",\"metrics\":{\"node.net.in.dropped\":0.0,\"node.net.out.errors\":0.0,\"node.net.out.bytes\":5.4246745E7,\"node.net.in.bytes\":3245766.0,\"node.net.out.dropped\":0.0,\"node.net.in.errors\":0.0},\"dimensions\":{\"app\":\"testapp.testinstance\",\"role\":\"tenants\",\"instanceName\":\"testinstance\",\"vespaVersion\":\"1.2.3\",\"clusterid\":\"clustId\",\"interface\":\"eth1\",\"parentHostname\":\"parent.host.name.yahoo.com\",\"flavor\":\"docker\",\"clustertype\":\"clustType\",\"tenantName\":\"tester\",\"zone\":\"dev.us-east-1\",\"host\":\"host1.test.yahoo.com\",\"state\":\"active\",\"applicationId\":\"tester.testapp.testinstance\",\"applicationName\":\"testapp\"},\"timestamp\":0}'", arg);

        assertEquals(expectedMetrics, actualMetrics);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetRelevantMetricsForReadyNode() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();
        ClassLoader classLoader = getClass().getClassLoader();

        final ContainerNodeSpec nodeSpec = nodeSpecBuilder
                .nodeState(Node.State.ready)
                .build();

        NodeAgentImpl nodeAgent = makeNodeAgent(null, false);

        when(nodeRepository.getContainerNodeSpec(eq(hostName))).thenReturn(Optional.of(nodeSpec));
        when(dockerOperations.getContainerStats(eq(containerName))).thenReturn(Optional.empty());

        nodeAgent.converge(); // Run the converge loop once to initialize lastNodeSpec

        nodeAgent.updateContainerNodeMetrics(5);

        File expectedMetricsFile = new File(classLoader.getResource("docker.stats.metrics.ready.expected.json").getFile());
        Set<Map<String, Object>> expectedMetrics = objectMapper.readValue(expectedMetricsFile, Set.class);
        Set<Map<String, Object>> actualMetrics = metricReceiver.getAllMetricsRaw();

        assertEquals(expectedMetrics, actualMetrics);
    }


    private NodeAgentImpl makeNodeAgent(DockerImage dockerImage, boolean isRunning) {
        Optional<Container> container = dockerImage != null ?
                Optional.of(new Container(
                        hostName,
                        dockerImage,
                        containerName,
                        isRunning ? Container.State.RUNNING : Container.State.EXITED,
                        isRunning ? 1 : 0,
                        clock.instant().toString())) :
                Optional.empty();

        when(dockerOperations.getContainerStats(any())).thenReturn(Optional.of(emptyContainerStats));
        when(dockerOperations.getContainer(eq(containerName))).thenReturn(container);
        doNothing().when(storageMaintainer).writeFilebeatConfig(any(), any());
        doNothing().when(storageMaintainer).writeMetricsConfig(any(), any());

        return new NodeAgentImpl(hostName, nodeRepository, orchestrator, dockerOperations,
                Optional.of(storageMaintainer), metricReceiver, environment, clock, Optional.of(aclMaintainer));
    }
}

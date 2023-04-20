// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.OrchestratorStatus;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.reports.DropDocumentsReport;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.node.admin.container.Container;
import com.yahoo.vespa.hosted.node.admin.container.ContainerId;
import com.yahoo.vespa.hosted.node.admin.container.ContainerName;
import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.container.ContainerResources;
import com.yahoo.vespa.hosted.node.admin.container.RegistryCredentials;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.identity.CredentialsMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.VespaServiceDumper;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.ConvergenceException;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.nio.file.FileSystem;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 * @author Øyvind Bakksjø
 */
public class NodeAgentImplTest {
    private static final NodeResources resources = new NodeResources(2, 16, 250, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.local);
    private static final Version vespaVersion = Version.fromString("1.2.3");
    private static final ContainerId containerId = new ContainerId("af23");
    private static final String hostName = "host1.test.yahoo.com";

    private final NodeAgentContextSupplier contextSupplier = mock(NodeAgentContextSupplier.class);
    private final DockerImage dockerImage = DockerImage.fromString("registry.example.com/repo/image");
    private final ContainerOperations containerOperations = mock(ContainerOperations.class);
    private final NodeRepository nodeRepository = mock(NodeRepository.class);
    private final Orchestrator orchestrator = mock(Orchestrator.class);
    private final StorageMaintainer storageMaintainer = mock(StorageMaintainer.class);
    private final AclMaintainer aclMaintainer = mock(AclMaintainer.class);
    private final HealthChecker healthChecker = mock(HealthChecker.class);
    private final CredentialsMaintainer credentialsMaintainer = mock(CredentialsMaintainer.class);
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private final ManualClock clock = new ManualClock(Instant.now());
    private final FileSystem fileSystem = TestFileSystem.create();

    @BeforeEach
    public void setUp() {
        when(containerOperations.suspendNode(any())).thenReturn("");
        when(containerOperations.resumeNode(any())).thenReturn("");
        when(containerOperations.restartVespa(any())).thenReturn("");
        when(containerOperations.startServices(any())).thenReturn("");
        when(containerOperations.stopServices(any())).thenReturn("");
    }

    @Test
    void upToDateContainerIsUntouched() {
        final NodeSpec node = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion)
                .orchestratorStatus(OrchestratorStatus.NO_REMARKS)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);
        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));

        nodeAgent.doConverge(context);

        verify(containerOperations, never()).removeContainer(eq(context), any());
        verify(orchestrator, never()).suspend(any(String.class));
        verify(containerOperations, never()).pullImageAsyncIfNeeded(any(), any(), any());

        final InOrder inOrder = inOrder(containerOperations, orchestrator, nodeRepository);
        // TODO: Verify this isn't run unless 1st time
        inOrder.verify(containerOperations, never()).startServices(eq(context));
        inOrder.verify(containerOperations, times(1)).resumeNode(eq(context));
        inOrder.verify(orchestrator, never()).resume(hostName);
    }

    @Test
    void verifyRemoveOldFilesIfDiskFull() {
        final NodeSpec node = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);
        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));

        nodeAgent.doConverge(context);

        verify(storageMaintainer, times(1)).cleanDiskIfFull(eq(context));
    }

    @Test
    void startsAfterStoppingServices() {
        final InOrder inOrder = inOrder(containerOperations);
        final NodeSpec node = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);
        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));

        nodeAgent.doConverge(context);
        inOrder.verify(containerOperations, never()).startServices(eq(context));
        inOrder.verify(containerOperations, times(1)).resumeNode(eq(context));

        nodeAgent.stopForHostSuspension(context);
        nodeAgent.doConverge(context);
        inOrder.verify(containerOperations, never()).startServices(eq(context));
        inOrder.verify(containerOperations, times(1)).resumeNode(eq(context)); // Expect a resume, but no start services

        // No new suspends/stops, so no need to resume/start
        nodeAgent.doConverge(context);
        inOrder.verify(containerOperations, never()).startServices(eq(context));
        inOrder.verify(containerOperations, never()).resumeNode(eq(context));

        nodeAgent.stopForHostSuspension(context);
        nodeAgent.doConverge(context);
        inOrder.verify(containerOperations, times(1)).createContainer(eq(context), any());
        inOrder.verify(containerOperations, times(1)).startContainer(eq(context));
        inOrder.verify(containerOperations, times(0)).startServices(eq(context)); // done as part of startContainer
        inOrder.verify(containerOperations, times(1)).resumeNode(eq(context));
    }

    @Test
    void absentContainerCausesStart() {
        final NodeSpec node = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(null, false);

        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));
        when(containerOperations.pullImageAsyncIfNeeded(any(), eq(dockerImage), any())).thenReturn(false);

        nodeAgent.doConverge(context);

        verify(containerOperations, never()).removeContainer(eq(context), any());
        verify(containerOperations, never()).startServices(any());
        verify(orchestrator, never()).suspend(any(String.class));

        final InOrder inOrder = inOrder(containerOperations, orchestrator, nodeRepository, aclMaintainer, healthChecker);
        inOrder.verify(containerOperations, times(1)).pullImageAsyncIfNeeded(any(), eq(dockerImage), any());
        inOrder.verify(containerOperations, times(1)).createContainer(eq(context), any());
        inOrder.verify(containerOperations, times(1)).startContainer(eq(context));
        inOrder.verify(aclMaintainer, times(1)).converge(eq(context));
        inOrder.verify(containerOperations, times(1)).resumeNode(eq(context));
        inOrder.verify(healthChecker, times(1)).verifyHealth(eq(context));
        inOrder.verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes().withDockerImage(dockerImage).withVespaVersion(vespaVersion).withRebootGeneration(0));
        inOrder.verify(orchestrator, never()).resume(hostName);
    }

    @Test
    void containerIsNotStoppedIfNewImageMustBePulled() {
        final DockerImage newDockerImage = DockerImage.fromString("registry.example.com/repo/new-image");
        final NodeSpec node = nodeBuilder(NodeState.active)
                .wantedDockerImage(newDockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));
        when(containerOperations.pullImageAsyncIfNeeded(any(), any(), any())).thenReturn(true);

        nodeAgent.doConverge(context);

        verify(orchestrator, never()).suspend(any(String.class));
        verify(orchestrator, never()).resume(any(String.class));
        verify(containerOperations, never()).removeContainer(eq(context), any());

        final InOrder inOrder = inOrder(containerOperations);
        inOrder.verify(containerOperations, times(1)).pullImageAsyncIfNeeded(any(), eq(newDockerImage), any());
    }

    @Test
    void containerIsUpdatedIfCpuChanged() {
        NodeSpec.Builder specBuilder = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion)
                .orchestratorStatus(OrchestratorStatus.NO_REMARKS);

        NodeAgentContext firstContext = createContext(specBuilder.build());
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(containerOperations.pullImageAsyncIfNeeded(any(), any(), any())).thenReturn(true);

        InOrder inOrder = inOrder(orchestrator, containerOperations);

        nodeAgent.doConverge(firstContext);
        inOrder.verify(orchestrator, never()).resume(any(String.class));

        NodeAgentContext secondContext = createContext(specBuilder.diskGb(200).build());
        nodeAgent.doConverge(secondContext);
        inOrder.verify(orchestrator, never()).resume(any(String.class));

        NodeAgentContext thirdContext = NodeAgentContextImpl.builder(specBuilder.vcpu(5).build()).fileSystem(fileSystem).cpuSpeedUp(1.25).build();
        nodeAgent.doConverge(thirdContext);
        ContainerResources resourcesAfterThird = ContainerResources.from(0, 4, 16);
        mockGetContainer(dockerImage, resourcesAfterThird, true);

        inOrder.verify(orchestrator, never()).suspend(any());
        inOrder.verify(containerOperations).updateContainer(eq(thirdContext), eq(containerId), eq(resourcesAfterThird));
        inOrder.verify(containerOperations, never()).removeContainer(any(), any());
        inOrder.verify(containerOperations, never()).startContainer(any());
        inOrder.verify(orchestrator, never()).resume(any());

        // No changes
        nodeAgent.converge(thirdContext);
        inOrder.verify(orchestrator, never()).suspend(any());
        inOrder.verify(containerOperations, never()).updateContainer(eq(thirdContext), eq(containerId), any());
        inOrder.verify(containerOperations, never()).removeContainer(any(), any());
        inOrder.verify(orchestrator, never()).resume(any());

        // Set the feature flag
        flagSource.withDoubleFlag(PermanentFlags.CONTAINER_CPU_CAP.id(), 2.3);

        nodeAgent.doConverge(thirdContext);
        inOrder.verify(containerOperations).updateContainer(eq(thirdContext), eq(containerId), eq(ContainerResources.from(9.2, 4, 16)));
        inOrder.verify(orchestrator, never()).resume(any());
    }

    @Test
    void containerIsRecreatedIfMemoryChanged() {
        NodeSpec.Builder specBuilder = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion)
                .wantedRestartGeneration(2).currentRestartGeneration(1);

        NodeAgentContext firstContext = createContext(specBuilder.build());
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(containerOperations.pullImageAsyncIfNeeded(any(), any(), any())).thenReturn(true);

        nodeAgent.doConverge(firstContext);
        NodeAgentContext secondContext = createContext(specBuilder.memoryGb(20).build());
        nodeAgent.doConverge(secondContext);
        ContainerResources resourcesAfterThird = ContainerResources.from(0, 2, 20);
        mockGetContainer(dockerImage, resourcesAfterThird, true);

        InOrder inOrder = inOrder(orchestrator, containerOperations, nodeRepository);
        inOrder.verify(orchestrator).resume(any(String.class));
        inOrder.verify(containerOperations).removeContainer(eq(secondContext), any());
        inOrder.verify(containerOperations, never()).updateContainer(any(), any(), any());
        inOrder.verify(containerOperations, never()).restartVespa(any());
        inOrder.verify(nodeRepository).updateNodeAttributes(eq(hostName), eq(new NodeAttributes().withRestartGeneration(2).withRebootGeneration(0)));

        nodeAgent.doConverge(secondContext);
        inOrder.verify(orchestrator).resume(any(String.class));
        inOrder.verify(containerOperations, never()).updateContainer(any(), any(), any());
        inOrder.verify(containerOperations, never()).removeContainer(any(), any());
    }

    @Test
    void noRestartIfOrchestratorSuspendFails() {
        final NodeSpec node = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion)
                .wantedRestartGeneration(2).currentRestartGeneration(1)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        doThrow(new OrchestratorException("Denied")).when(orchestrator).suspend(eq(hostName));
        try {
            nodeAgent.doConverge(context);
            fail("Expected to throw an exception");
        } catch (OrchestratorException ignored) {
        }

        verify(containerOperations, never()).createContainer(eq(context), any());
        verify(containerOperations, never()).startContainer(eq(context));
        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository, never()).updateNodeAttributes(any(String.class), any(NodeAttributes.class));

        // Verify aclMaintainer is called even if suspension fails
        verify(aclMaintainer, times(1)).converge(eq(context));
    }

    @Test
    void recreatesContainerIfRebootWanted() {
        final long wantedRebootGeneration = 2;
        final NodeSpec node = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion)
                .wantedRebootGeneration(wantedRebootGeneration).currentRebootGeneration(1)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));
        when(containerOperations.pullImageAsyncIfNeeded(any(), eq(dockerImage), any())).thenReturn(false);
        doThrow(ConvergenceException.ofTransient("Connection refused")).doNothing()
                .when(healthChecker).verifyHealth(eq(context));

        try {
            nodeAgent.doConverge(context);
        } catch (ConvergenceException ignored) {
        }

        // First time we fail to resume because health verification fails
        verify(orchestrator, times(1)).suspend(eq(hostName));
        verify(containerOperations, times(1)).removeContainer(eq(context), any());
        verify(containerOperations, times(1)).createContainer(eq(context), any());
        verify(containerOperations, times(1)).startContainer(eq(context));
        verify(orchestrator, never()).resume(eq(hostName));
        verify(nodeRepository, never()).updateNodeAttributes(any(), any());

        nodeAgent.doConverge(context);

        // Do not reboot the container again
        verify(containerOperations, times(1)).removeContainer(eq(context), any());
        verify(containerOperations, times(1)).createContainer(eq(context), any());
        verify(orchestrator, times(1)).resume(eq(hostName));
        verify(nodeRepository, times(1)).updateNodeAttributes(eq(hostName), eq(new NodeAttributes()
                .withRebootGeneration(wantedRebootGeneration)));
    }

    @Test
    void failedNodeRunningContainerShouldStillBeRunning() {
        final NodeSpec node = nodeBuilder(NodeState.failed)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));

        nodeAgent.doConverge(context);

        verify(containerOperations, never()).removeContainer(eq(context), any());
        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository, never()).updateNodeAttributes(eq(hostName), any());
    }

    @Test
    void readyNodeLeadsToNoAction() {
        final NodeSpec node = nodeBuilder(NodeState.ready).build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(null, false);

        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));

        nodeAgent.doConverge(context);
        nodeAgent.doConverge(context);
        nodeAgent.doConverge(context);

        // Should only be called once, when we initialize
        verify(containerOperations, times(1)).getContainer(eq(context));
        verify(containerOperations, never()).removeContainer(eq(context), any());
        verify(containerOperations, never()).createContainer(eq(context), any());
        verify(containerOperations, never()).startContainer(eq(context));
        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository, never()).updateNodeAttributes(eq(hostName), any());
    }

    @Test
    void inactiveNodeRunningContainerShouldStillBeRunning() {
        final NodeSpec node = nodeBuilder(NodeState.inactive)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));

        nodeAgent.doConverge(context);

        final InOrder inOrder = inOrder(storageMaintainer, containerOperations);
        inOrder.verify(containerOperations, never()).removeContainer(eq(context), any());

        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository, never()).updateNodeAttributes(eq(hostName), any());
    }

    @Test
    void reservedNodeDoesNotUpdateNodeRepoWithVersion() {
        final NodeSpec node = nodeBuilder(NodeState.reserved)
                .wantedDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(null, false);

        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));

        nodeAgent.doConverge(context);

        verify(nodeRepository, never()).updateNodeAttributes(eq(hostName), any());
    }

    private void nodeRunningContainerIsTakenDownAndCleanedAndRecycled(NodeState nodeState, Optional<Long> wantedRestartGeneration) {
        NodeSpec.Builder builder = nodeBuilder(nodeState)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage);
        wantedRestartGeneration.ifPresent(restartGeneration -> builder
                .wantedRestartGeneration(restartGeneration).currentRestartGeneration(restartGeneration));

        NodeSpec node = builder.build();
        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));

        nodeAgent.doConverge(context);

        final InOrder inOrder = inOrder(storageMaintainer, containerOperations, nodeRepository);
        inOrder.verify(containerOperations, times(1)).stopServices(eq(context));
        inOrder.verify(storageMaintainer, times(1)).handleCoreDumpsForContainer(eq(context), any(), eq(true));
        inOrder.verify(containerOperations, times(1)).removeContainer(eq(context), any());
        inOrder.verify(storageMaintainer, times(1)).archiveNodeStorage(eq(context));
        inOrder.verify(nodeRepository, times(1)).setNodeState(eq(hostName), eq(NodeState.ready));

        verify(containerOperations, never()).createContainer(eq(context), any());
        verify(containerOperations, never()).startContainer(eq(context));
        verify(containerOperations, never()).suspendNode(eq(context));
        verify(containerOperations, times(1)).stopServices(eq(context));
        verify(orchestrator, never()).resume(any(String.class));
        verify(orchestrator, never()).suspend(any(String.class));
        // current Docker image and vespa version should be cleared
        verify(nodeRepository, times(1)).updateNodeAttributes(
                eq(hostName), eq(new NodeAttributes().withDockerImage(DockerImage.EMPTY).withVespaVersion(Version.emptyVersion)));
    }

    @Test
    void dirtyNodeRunningContainerIsTakenDownAndCleanedAndRecycled() {
        nodeRunningContainerIsTakenDownAndCleanedAndRecycled(NodeState.dirty, Optional.of(1L));
    }

    @Test
    void dirtyNodeRunningContainerIsTakenDownAndCleanedAndRecycledNoRestartGeneration() {
        nodeRunningContainerIsTakenDownAndCleanedAndRecycled(NodeState.dirty, Optional.empty());
    }

    @Test
    void provisionedNodeIsMarkedAsReady() {
        final NodeSpec node = nodeBuilder(NodeState.provisioned)
                .wantedDockerImage(dockerImage)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(null, false);
        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));

        nodeAgent.doConverge(context);
        verify(nodeRepository, times(1)).setNodeState(eq(hostName), eq(NodeState.ready));
    }

    @Test
    void testRestartDeadContainerAfterNodeAdminRestart() {
        final NodeSpec node = nodeBuilder(NodeState.active)
                .currentDockerImage(dockerImage).wantedDockerImage(dockerImage)
                .currentVespaVersion(vespaVersion)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, false);

        when(nodeRepository.getOptionalNode(eq(hostName))).thenReturn(Optional.of(node));

        nodeAgent.doConverge(context);

        verify(containerOperations, times(1)).removeContainer(eq(context), any());
        verify(containerOperations, times(1)).createContainer(eq(context), any());
        verify(containerOperations, times(1)).startContainer(eq(context));
    }

    @Test
    void resumeProgramRunsUntilSuccess() {
        final NodeSpec node = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .currentVespaVersion(vespaVersion)
                .wantedRestartGeneration(1).currentRestartGeneration(1)
                .orchestratorStatus(OrchestratorStatus.ALLOWED_TO_BE_DOWN)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getOptionalNode(eq(hostName))).thenReturn(Optional.of(node));

        final InOrder inOrder = inOrder(orchestrator, containerOperations, nodeRepository);
        doThrow(new RuntimeException("Failed 1st time"))
                .doReturn("")
                .when(containerOperations).resumeNode(eq(context));

        // 1st try
        try {
            nodeAgent.doConverge(context);
            fail("Expected to throw an exception");
        } catch (RuntimeException ignored) {
        }

        inOrder.verify(containerOperations, times(1)).resumeNode(any());
        inOrder.verifyNoMoreInteractions();

        // 2nd try
        nodeAgent.doConverge(context);

        inOrder.verify(containerOperations).resumeNode(any());
        inOrder.verify(orchestrator).resume(hostName);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void start_container_subtask_failure_leads_to_container_restart() {
        final NodeSpec node = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion)
                .wantedRestartGeneration(1).currentRestartGeneration(1)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = spy(makeNodeAgent(null, false));

        when(containerOperations.pullImageAsyncIfNeeded(any(), eq(dockerImage), any())).thenReturn(false);
        doThrow(new RuntimeException("Failed to set up network")).doNothing().when(containerOperations).startContainer(eq(context));

        try {
            nodeAgent.doConverge(context);
            fail("Expected to get RuntimeException");
        } catch (RuntimeException ignored) {
        }

        verify(containerOperations, never()).removeContainer(eq(context), any());
        verify(containerOperations, times(1)).createContainer(eq(context), any());
        verify(containerOperations, times(1)).startContainer(eq(context));
        verify(nodeAgent, never()).resumeNodeIfNeeded(any());

        // The docker container was actually started and is running, but subsequent exec calls to set up
        // networking failed
        mockGetContainer(dockerImage, true);
        nodeAgent.doConverge(context);

        verify(containerOperations, times(1)).removeContainer(eq(context), any());
        verify(containerOperations, times(2)).createContainer(eq(context), any());
        verify(containerOperations, times(2)).startContainer(eq(context));
        verify(nodeAgent, times(1)).resumeNodeIfNeeded(any());
    }

    @Test
    void testRunningConfigServer() {
        final NodeSpec node = nodeBuilder(NodeState.active)
                .type(NodeType.config)
                .wantedDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion)
                .orchestratorStatus(OrchestratorStatus.ALLOWED_TO_BE_DOWN)
                .build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(null, false);

        when(nodeRepository.getOptionalNode(hostName)).thenReturn(Optional.of(node));
        when(containerOperations.pullImageAsyncIfNeeded(any(), eq(dockerImage), any())).thenReturn(false);

        nodeAgent.doConverge(context);

        verify(containerOperations, never()).removeContainer(eq(context), any());
        verify(orchestrator, never()).suspend(any(String.class));

        final InOrder inOrder = inOrder(containerOperations, orchestrator, nodeRepository, aclMaintainer);
        inOrder.verify(containerOperations, times(1)).pullImageAsyncIfNeeded(any(), eq(dockerImage), any());
        inOrder.verify(containerOperations, times(1)).createContainer(eq(context), any());
        inOrder.verify(containerOperations, times(1)).startContainer(eq(context));
        inOrder.verify(aclMaintainer, times(1)).converge(eq(context));
        inOrder.verify(containerOperations, times(1)).resumeNode(eq(context));
        inOrder.verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes().withDockerImage(dockerImage).withVespaVersion(vespaVersion).withRebootGeneration(0));
        inOrder.verify(orchestrator).resume(hostName);
    }


    // Tests that only containers without owners are stopped
    @Test
    void testThatStopContainerDependsOnOwnerPresent() {
        verifyThatContainerIsStopped(NodeState.parked, Optional.empty());
        verifyThatContainerIsStopped(NodeState.parked, Optional.of(ApplicationId.defaultId()));
        verifyThatContainerIsStopped(NodeState.failed, Optional.empty());
        verifyThatContainerIsStopped(NodeState.failed, Optional.of(ApplicationId.defaultId()));
        verifyThatContainerIsStopped(NodeState.inactive, Optional.of(ApplicationId.defaultId()));
    }

    @Test
    void initial_cpu_cap_test() {
        NodeSpec.Builder specBuilder = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion);

        NodeAgentContext context = createContext(specBuilder.build());
        NodeAgentImpl nodeAgent = makeNodeAgent(null, false, Duration.ofSeconds(30));

        InOrder inOrder = inOrder(orchestrator, containerOperations);

        ConvergenceException healthCheckException = ConvergenceException.ofTransient("Not yet up");
        doThrow(healthCheckException).when(healthChecker).verifyHealth(any());
        for (int i = 0; i < 3; i++) {
            try {
                nodeAgent.doConverge(context);
                fail("Expected to fail with health check exception");
            } catch (ConvergenceException e) {
                assertEquals(healthCheckException, e);
            }
            clock.advance(Duration.ofSeconds(30));
        }

        doNothing().when(healthChecker).verifyHealth(any());
        try {
            nodeAgent.doConverge(context);
            fail("Expected to fail due to warm up period not yet done");
        } catch (ConvergenceException e) {
            assertEquals("Refusing to resume until warm up period ends (in PT30S)", e.getMessage());
        }
        inOrder.verify(orchestrator, never()).resume(any());
        inOrder.verify(orchestrator, never()).suspend(any());
        inOrder.verify(containerOperations, never()).updateContainer(any(), any(), any());


        clock.advance(Duration.ofSeconds(31));
        nodeAgent.doConverge(context);

        inOrder.verify(orchestrator, never()).suspend(any());
        inOrder.verify(containerOperations).updateContainer(eq(context), eq(containerId), eq(ContainerResources.from(0, 2, 16)));
        inOrder.verify(containerOperations, never()).removeContainer(any(), any());
        inOrder.verify(containerOperations, never()).startContainer(any());
        inOrder.verify(orchestrator, never()).resume(any());

        // No changes
        nodeAgent.converge(context);
        inOrder.verify(orchestrator, never()).suspend(any());
        inOrder.verify(containerOperations, never()).updateContainer(eq(context), eq(containerId), any());
        inOrder.verify(containerOperations, never()).removeContainer(any(), any());
        inOrder.verify(orchestrator, never()).resume(any());
    }

    @Test
    void resumes_normally_if_container_is_already_capped_on_start() {
        NodeSpec.Builder specBuilder = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion)
                .wantedRestartGeneration(1).currentRestartGeneration(1);

        NodeAgentContext context = createContext(specBuilder.build());
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true, Duration.ofSeconds(30));
        mockGetContainer(dockerImage, ContainerResources.from(0, 2, 16), true);

        InOrder inOrder = inOrder(orchestrator, containerOperations);

        nodeAgent.doConverge(context);

        nodeAgent.converge(context);
        inOrder.verify(orchestrator, never()).suspend(any(String.class));
        inOrder.verify(containerOperations, never()).updateContainer(eq(context), eq(containerId), any());
        inOrder.verify(containerOperations, never()).removeContainer(any(), any());
        inOrder.verify(orchestrator, never()).resume(any(String.class));
    }

    @Test
    void uncaps_and_caps_cpu_for_services_restart() {
        NodeSpec.Builder specBuilder = nodeBuilder(NodeState.active)
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .wantedVespaVersion(vespaVersion).currentVespaVersion(vespaVersion)
                .wantedRestartGeneration(2).currentRestartGeneration(1);

        NodeAgentContext context = createContext(specBuilder.build());
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true, Duration.ofSeconds(30));
        mockGetContainer(dockerImage, ContainerResources.from(2, 2, 16), true);

        InOrder inOrder = inOrder(orchestrator, containerOperations);

        nodeAgent.converge(context);
        inOrder.verify(orchestrator, times(1)).suspend(eq(hostName));
        inOrder.verify(containerOperations, times(1)).updateContainer(eq(context), eq(containerId), eq(ContainerResources.from(0, 0, 16)));
        inOrder.verify(containerOperations, times(1)).restartVespa(eq(context));

        mockGetContainer(dockerImage, ContainerResources.from(0, 0, 16), true);
        doNothing().when(healthChecker).verifyHealth(any());
        try {
            nodeAgent.doConverge(context);
            fail("Expected to fail due to warm up period not yet done");
        } catch (ConvergenceException e) {
            assertEquals("Refusing to resume until warm up period ends (in PT30S)", e.getMessage());
        }
        inOrder.verify(orchestrator, never()).resume(any());
        inOrder.verify(orchestrator, never()).suspend(any());
        inOrder.verify(containerOperations, never()).updateContainer(any(), any(), any());


        clock.advance(Duration.ofSeconds(31));
        nodeAgent.doConverge(context);
        inOrder.verify(orchestrator, times(1)).resume(eq(hostName));
    }

    @Test
    void drop_all_documents() {
        InOrder inOrder = inOrder(orchestrator, nodeRepository);
        BiFunction<NodeState, DropDocumentsReport, NodeSpec> specBuilder = (state, report) -> (report == null ?
                nodeBuilder(state) : nodeBuilder(state).report(DropDocumentsReport.reportId(), report.toJsonNode()))
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage)
                .build();
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true, Duration.ofSeconds(30));

        NodeAgentContext context = createContext(specBuilder.apply(NodeState.active, null));
        UnixPath indexPath = new UnixPath(context.paths().underVespaHome("var/db/vespa/search/cluster.foo/0/doc")).createParents().createNewFile();
        mockGetContainer(dockerImage, ContainerResources.from(2, 2, 16), true);
        assertTrue(indexPath.exists());

        // Initially no changes, index is not dropped
        nodeAgent.converge(context);
        assertTrue(indexPath.exists());
        inOrder.verifyNoMoreInteractions();

        context = createContext(specBuilder.apply(NodeState.active, new DropDocumentsReport(1L, null, null, null)));
        nodeAgent.converge(context);
        verify(containerOperations).removeContainer(eq(context), any());
        assertFalse(indexPath.exists());
        inOrder.verify(nodeRepository).updateNodeAttributes(eq(hostName), eq(new NodeAttributes().withReport(DropDocumentsReport.reportId(), new DropDocumentsReport(1L, clock.millis(), null, null).toJsonNode())));
        inOrder.verifyNoMoreInteractions();

        // After droppedAt and before readiedAt are set, we cannot proceed
        mockGetContainer(null, false);
        context = createContext(specBuilder.apply(NodeState.active, new DropDocumentsReport(1L, 2L, null, null)));
        nodeAgent.converge(context);
        verify(containerOperations, never()).removeContainer(eq(context), any());
        verify(containerOperations, never()).startContainer(eq(context));
        inOrder.verifyNoMoreInteractions();

        context = createContext(specBuilder.apply(NodeState.active, new DropDocumentsReport(1L, 2L, 3L, null)));
        nodeAgent.converge(context);
        verify(containerOperations).startContainer(eq(context));
        inOrder.verifyNoMoreInteractions();

        mockGetContainer(dockerImage, ContainerResources.from(0, 2, 16), true);
        clock.advance(Duration.ofSeconds(31));
        nodeAgent.converge(context);
        verify(containerOperations, times(1)).startContainer(eq(context));
        verify(containerOperations, never()).removeContainer(eq(context), any());
        inOrder.verify(nodeRepository).updateNodeAttributes(eq(hostName), eq(new NodeAttributes()
                .withRebootGeneration(0)
                .withReport(DropDocumentsReport.reportId(), new DropDocumentsReport(1L, 2L, 3L, clock.millis()).toJsonNode())));
        inOrder.verifyNoMoreInteractions();
    }

    private void verifyThatContainerIsStopped(NodeState nodeState, Optional<ApplicationId> owner) {
        NodeSpec.Builder nodeBuilder = nodeBuilder(nodeState)
                .type(NodeType.tenant)
                .flavor("docker")
                .wantedDockerImage(dockerImage).currentDockerImage(dockerImage);

        owner.ifPresent(nodeBuilder::owner);
        NodeSpec node = nodeBuilder.build();

        NodeAgentContext context = createContext(node);
        NodeAgentImpl nodeAgent = makeNodeAgent(dockerImage, true);

        when(nodeRepository.getOptionalNode(eq(hostName))).thenReturn(Optional.of(node));

        nodeAgent.doConverge(context);

        verify(containerOperations, never()).removeContainer(eq(context), any());
        if (owner.isPresent()) {
            verify(containerOperations, never()).stopServices(eq(context));
        } else {
            verify(containerOperations, times(1)).stopServices(eq(context));
            nodeAgent.doConverge(context);
            // Should not be called more than once, have already been stopped
            verify(containerOperations, times(1)).stopServices(eq(context));
        }
    }

    private NodeAgentImpl makeNodeAgent(DockerImage dockerImage, boolean isRunning) {
        return makeNodeAgent(dockerImage, isRunning, Duration.ofSeconds(-1));
    }

    private NodeAgentImpl makeNodeAgent(DockerImage dockerImage, boolean isRunning, Duration warmUpDuration) {
        mockGetContainer(dockerImage, isRunning);
        doAnswer(invoc -> {
            NodeAgentContext context = invoc.getArgument(0, NodeAgentContext.class);
            ContainerResources resources = invoc.getArgument(1, ContainerResources.class);
            mockGetContainer(context.node().wantedDockerImage().get(), resources, true);
            return null;
        }).when(containerOperations).createContainer(any(), any());

        doAnswer(invoc -> {
            NodeAgentContext context = invoc.getArgument(0, NodeAgentContext.class);
            ContainerResources resources = invoc.getArgument(2, ContainerResources.class);
            mockGetContainer(context.node().wantedDockerImage().get(), resources, true);
            return null;
        }).when(containerOperations).updateContainer(any(), any(), any());

        return new NodeAgentImpl(contextSupplier, nodeRepository, orchestrator, containerOperations,
                                 () -> RegistryCredentials.none, storageMaintainer, flagSource,
                                 List.of(credentialsMaintainer), Optional.of(aclMaintainer), Optional.of(healthChecker),
                                 clock, warmUpDuration, VespaServiceDumper.DUMMY_INSTANCE, List.of());
    }

    private void mockGetContainer(DockerImage dockerImage, boolean isRunning) {
        mockGetContainer(dockerImage, ContainerResources.from(0, resources.vcpu(), resources.memoryGb()), isRunning);
    }

    private void mockGetContainer(DockerImage dockerImage, ContainerResources containerResources, boolean isRunning) {
        doAnswer(invoc -> {
            NodeAgentContext context = invoc.getArgument(0);
            if (!hostName.equals(context.hostname().value()))
                throw new IllegalArgumentException();
            return dockerImage != null ?
                    Optional.of(new Container(
                            containerId,
                            ContainerName.fromHostname(hostName),
                            clock.instant(),
                            isRunning ? Container.State.running : Container.State.exited,
                            "image-id-1",
                            dockerImage,
                            Map.of(),
                            42,
                            43,
                            hostName,
                            containerResources,
                            List.of(),
                            true)) :
                    Optional.empty();
        }).when(containerOperations).getContainer(any());
    }
    
    private NodeAgentContext createContext(NodeSpec nodeSpec) {
        return NodeAgentContextImpl.builder(nodeSpec).fileSystem(fileSystem).build();
    }

    private NodeSpec.Builder nodeBuilder(NodeState state) {
        return NodeSpec.Builder.testSpec(hostName, state).realResources(resources);
    }
}

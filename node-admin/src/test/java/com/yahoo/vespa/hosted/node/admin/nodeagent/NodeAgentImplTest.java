// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.MaintenanceScheduler;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Optional;

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
    private static final Optional<Double> MIN_CPU_CORES = Optional.of(1.0);
    private static final Optional<Double> MIN_MAIN_MEMORY_AVAILABLE_GB = Optional.of(1.0);
    private static final Optional<Double> MIN_DISK_AVAILABLE_GB = Optional.of(1.0);

    private final String hostName = "hostname";
    private final DockerOperations dockerOperations = mock(DockerOperations.class);
    private final NodeRepository nodeRepository = mock(NodeRepository.class);
    private final Orchestrator orchestrator = mock(Orchestrator.class);
    private final MaintenanceScheduler maintenanceScheduler = mock(MaintenanceScheduler.class);

    private final NodeAgentImpl nodeAgent = new NodeAgentImpl(hostName, nodeRepository, orchestrator, dockerOperations, maintenanceScheduler);

    @Test
    public void upToDateContainerIsUntouched() throws Exception {
        final long restartGeneration = 1;
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerName containerName = new ContainerName("container-name");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec(
                hostName,
                Optional.of(dockerImage),
                containerName,
                Node.State.active,
                "tenant",
                "docker",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(restartGeneration),
                Optional.of(restartGeneration),
                MIN_CPU_CORES,
                MIN_MAIN_MEMORY_AVAILABLE_GB,
                MIN_DISK_AVAILABLE_GB);
        final boolean isRunning = true;
        final String vespaVersion = "7.8.9";

        when(dockerOperations.shouldScheduleDownloadOfImage(any())).thenReturn(false);
        when(dockerOperations.removeContainerIfNeeded(eq(nodeSpec), eq(hostName), any())).thenReturn(false);
        when(dockerOperations.startContainerIfNeeded(eq(nodeSpec))).thenReturn(false);
        when(dockerOperations.getVespaVersionOrNull(eq(containerName))).thenReturn(vespaVersion);
        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.tick();

        verify(orchestrator, never()).suspend(any(String.class));
        verify(dockerOperations, never()).scheduleDownloadOfImage(any(), any());

        final InOrder inOrder = inOrder(dockerOperations, orchestrator, nodeRepository);
        // TODO: Verify this isn't run unless 1st time
        inOrder.verify(dockerOperations, times(1)).executeResume(eq(containerName));
        // TODO: This should not happen when nothing is changed. Now it happens 1st time through.
        inOrder.verify(nodeRepository).updateNodeAttributes(
                hostName,
                new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withDockerImage(dockerImage)
                        .withVespaVersion(vespaVersion));
        inOrder.verify(orchestrator).resume(hostName);
    }

    @Test
    public void absentContainerCausesStart() throws Exception {
        final long restartGeneration = 1;
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerName containerName = new ContainerName("container-name");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec(
                hostName,
                Optional.of(dockerImage),
                containerName,
                Node.State.active,
                "tenant",
                "docker",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(restartGeneration),
                Optional.of(restartGeneration),
                MIN_CPU_CORES,
                MIN_MAIN_MEMORY_AVAILABLE_GB,
                MIN_DISK_AVAILABLE_GB);
        final String vespaVersion = "7.8.9";

        when(dockerOperations.shouldScheduleDownloadOfImage(any())).thenReturn(false);
        when(dockerOperations.removeContainerIfNeeded(eq(nodeSpec), eq(hostName), any())).thenReturn(true);
        when(dockerOperations.startContainerIfNeeded(eq(nodeSpec))).thenReturn(true);
        when(dockerOperations.getVespaVersionOrNull(eq(containerName))).thenReturn(vespaVersion);
        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.tick();

        verify(orchestrator, never()).suspend(any(String.class));
        verify(dockerOperations, never()).scheduleDownloadOfImage(any(), any());

        final InOrder inOrder = inOrder(dockerOperations, orchestrator, nodeRepository);
        inOrder.verify(dockerOperations, times(1)).executeResume(eq(containerName));
        inOrder.verify(nodeRepository).updateNodeAttributes(
                hostName, new NodeAttributes()
                        .withRestartGeneration(restartGeneration)
                        .withDockerImage(dockerImage)
                        .withVespaVersion(vespaVersion));
        inOrder.verify(orchestrator).resume(hostName);
    }

    @Test
    public void containerIsNotStoppedIfNewImageMustBePulled() throws Exception {
        final ContainerName containerName = new ContainerName("container");
        final DockerImage oldDockerImage = new DockerImage("old-image");
        final DockerImage newDockerImage = new DockerImage("new-image");
        final long wantedRestartGeneration = 2;
        final long currentRestartGeneration = 1;
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec(
                hostName,
                Optional.of(newDockerImage),
                containerName,
                Node.State.active,
                "tenant",
                "docker",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(wantedRestartGeneration),
                Optional.of(currentRestartGeneration),
                MIN_CPU_CORES,
                MIN_MAIN_MEMORY_AVAILABLE_GB,
                MIN_DISK_AVAILABLE_GB);
        final String vespaVersion = "7.8.9";

        when(dockerOperations.shouldScheduleDownloadOfImage(any())).thenReturn(true);
        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.tick();

        verify(orchestrator, never()).suspend(any(String.class));
        verify(orchestrator, never()).resume(any(String.class));
        verify(dockerOperations, never()).removeContainerIfNeeded(eq(nodeSpec), eq(hostName), any());

        final InOrder inOrder = inOrder(dockerOperations);
        inOrder.verify(dockerOperations, times(1)).shouldScheduleDownloadOfImage(eq(newDockerImage));
        inOrder.verify(dockerOperations, times(1)).scheduleDownloadOfImage(eq(nodeSpec), any());
    }

    @Test
    public void noRestartIfOrchestratorSuspendFails() throws Exception {
        final long wantedRestartGeneration = 2;
        final long currentRestartGeneration = 1;
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerName containerName = new ContainerName("container-name");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec(
                hostName,
                Optional.of(dockerImage),
                containerName,
                Node.State.active,
                "tenant",
                "docker",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(wantedRestartGeneration),
                Optional.of(currentRestartGeneration),
                MIN_CPU_CORES,
                MIN_MAIN_MEMORY_AVAILABLE_GB,
                MIN_DISK_AVAILABLE_GB);

        when(dockerOperations.shouldScheduleDownloadOfImage(any())).thenReturn(false);
        when(dockerOperations.removeContainerIfNeeded(eq(nodeSpec), eq(hostName), any()))
                .thenThrow(new OrchestratorException("Cannot suspend"));

        try {
            nodeAgent.tick();
            fail("Expected to throw an exception");
        } catch (Exception e) {
        }

        verify(dockerOperations, never()).startContainerIfNeeded(eq(nodeSpec));
        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository, never()).updateNodeAttributes(any(String.class), any(NodeAttributes.class));
    }

    @Test
    public void failedNodeRunningContainerIsTakenDown() throws Exception {
        final long restartGeneration = 1;
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerName containerName = new ContainerName("container-name");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec(
                hostName,
                Optional.of(dockerImage),
                containerName,
                Node.State.failed,
                "tenant",
                "docker",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(restartGeneration),
                Optional.of(restartGeneration),
                MIN_CPU_CORES,
                MIN_MAIN_MEMORY_AVAILABLE_GB,
                MIN_DISK_AVAILABLE_GB);

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.tick();

        verify(dockerOperations, times(1)).removeContainerIfNeeded(eq(nodeSpec), eq(hostName), any());
        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository, never()).updateNodeAttributes(any(String.class), any(NodeAttributes.class));
    }

    @Test
    public void inactiveNodeRunningContainerIsTakenDown() throws Exception {
        final long restartGeneration = 1;
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerName containerName = new ContainerName("container-name");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec(
                hostName,
                Optional.of(dockerImage),
                containerName,
                Node.State.inactive,
                "tenant",
                "docker",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(restartGeneration),
                Optional.of(restartGeneration),
                MIN_CPU_CORES,
                MIN_MAIN_MEMORY_AVAILABLE_GB,
                MIN_DISK_AVAILABLE_GB);

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.tick();

        final InOrder inOrder = inOrder(maintenanceScheduler, dockerOperations);
        inOrder.verify(maintenanceScheduler, times(1)).removeOldFilesFromNode(eq(containerName));
        inOrder.verify(dockerOperations, times(1)).removeContainerIfNeeded(eq(nodeSpec), eq(hostName), any());

        verify(orchestrator, never()).resume(any(String.class));
        verify(nodeRepository, never()).updateNodeAttributes(any(String.class), any(NodeAttributes.class));
    }

    private void nodeRunningContainerIsTakenDownAndCleanedAndRecycled(Node.State nodeState, Optional<Long> wantedRestartGeneration)
            throws Exception {
        final DockerImage dockerImage = new DockerImage("dockerImage");
        final ContainerName containerName = new ContainerName("container-name");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec(
                hostName,
                Optional.of(dockerImage),
                containerName,
                nodeState,
                "tenant",
                "docker",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                wantedRestartGeneration,
                wantedRestartGeneration, //currentRestartGeneration
                MIN_CPU_CORES,
                MIN_MAIN_MEMORY_AVAILABLE_GB,
                MIN_DISK_AVAILABLE_GB);

        when(nodeRepository.getContainerNodeSpec(hostName)).thenReturn(Optional.of(nodeSpec));

        nodeAgent.tick();

        final InOrder inOrder = inOrder(maintenanceScheduler, dockerOperations, nodeRepository);
        inOrder.verify(maintenanceScheduler, times(1)).removeOldFilesFromNode(eq(containerName));
        inOrder.verify(dockerOperations, times(1)).removeContainerIfNeeded(eq(nodeSpec), eq(hostName), any());
        inOrder.verify(maintenanceScheduler, times(1)).deleteContainerStorage(eq(containerName));
        inOrder.verify(nodeRepository, times(1)).markAsReady(eq(hostName));

        verify(dockerOperations, never()).startContainerIfNeeded(any());
        verify(orchestrator, never()).resume(any(String.class));
        // current Docker image and vespa version should be cleared
        verify(nodeRepository, times(1)).updateNodeAttributes(
                any(String.class), eq(new NodeAttributes().withDockerImage(new DockerImage("")).withVespaVersion("")));
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
    public void provisionedNodeWithNoContainerIsCleanedAndRecycled() throws Exception {
        nodeRunningContainerIsTakenDownAndCleanedAndRecycled(Node.State.provisioned, Optional.of(1L));
    }

    @Test
    public void resumeProgramRunsUntilSuccess() throws Exception {
        final long restartGeneration = 1;
        final String hostName = "hostname";
        final DockerImage wantedDockerImage = new DockerImage("wantedDockerImage");
        final ContainerName containerName = new ContainerName("container-name");
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec(
                hostName,
                Optional.of(wantedDockerImage),
                containerName,
                Node.State.active,
                "tenant",
                "docker",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(restartGeneration),
                Optional.of(restartGeneration),
                MIN_CPU_CORES,
                MIN_MAIN_MEMORY_AVAILABLE_GB,
                MIN_DISK_AVAILABLE_GB);
        final String vespaVersion = "7.8.9";

        when(nodeRepository.getContainerNodeSpec(eq(hostName))).thenReturn(Optional.of(nodeSpec));
        when(dockerOperations.shouldScheduleDownloadOfImage(eq(wantedDockerImage))).thenReturn(false);
        when(dockerOperations.removeContainerIfNeeded(eq(nodeSpec), eq(hostName), any())).thenReturn(true);
        when(dockerOperations.getVespaVersionOrNull(eq(containerName))).thenReturn(vespaVersion);

        doThrow(new RuntimeException("Failed 1st time"))
                .doNothing()
                .when(dockerOperations).executeResume(eq(containerName));

        final InOrder inOrder = inOrder(orchestrator, dockerOperations, nodeRepository);

        // 1st try
        try {
            nodeAgent.tick();
            fail("Expected to throw an exception");
        } catch (RuntimeException e) {
        }

        inOrder.verify(dockerOperations, times(1)).executeResume(any());
        inOrder.verifyNoMoreInteractions();

        // 2nd try
        nodeAgent.tick();

        inOrder.verify(dockerOperations).executeResume(any());
        inOrder.verify(orchestrator).resume(hostName);
        inOrder.verifyNoMoreInteractions();
    }
}

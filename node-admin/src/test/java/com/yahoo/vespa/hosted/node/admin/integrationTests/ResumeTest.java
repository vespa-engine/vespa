// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.nodeagent.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertThat;

/**
 * Scenario test for NodeAdminStateUpdater.
 *
 * @author dybis
 */
public class ResumeTest {
    @Before
    public void resetMocks() {
        try {
            OrchestratorMock.semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        MaintenanceSchedulerMock.reset();
        OrchestratorMock.reset();
        NodeRepoMock.reset();
        DockerMock.reset();
    }

    @After
    public void after() {
        OrchestratorMock.semaphore.release();
    }

    @Test
    public void test() throws InterruptedException {
        NodeRepoMock nodeRepositoryMock = new NodeRepoMock();
        MaintenanceSchedulerMock maintenanceSchedulerMock = new MaintenanceSchedulerMock();
        OrchestratorMock orchestratorMock = new OrchestratorMock();
        DockerMock dockerMock = new DockerMock();

        Function<HostName, NodeAgent> nodeAgentFactory = (hostName) ->
                new NodeAgentImpl(hostName, nodeRepositoryMock, orchestratorMock, new DockerOperations(dockerMock), maintenanceSchedulerMock);
        NodeAdmin nodeAdmin = new NodeAdminImpl(dockerMock, nodeAgentFactory, maintenanceSchedulerMock, 100);

        NodeRepoMock.addContainerNodeSpec(new ContainerNodeSpec(
                new HostName("hostName"),
                Optional.of(new DockerImage("dockerImage")),
                new ContainerName("container"),
                NodeState.ACTIVE,
                Optional.of(1L),
                Optional.of(1L),
                Optional.of(1d),
                Optional.of(1d),
                Optional.of(1d)));

        NodeAdminStateUpdater updater = new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 1, orchestratorMock, "basehostname");

        // Wait for node admin to be notified with node repo state and the docker container has been started
        while (nodeAdmin.getListOfHosts().size() == 0) {
            Thread.sleep(10);
        }

        while (!DockerMock.getRequests().startsWith("startContainer with DockerImage: DockerImage { imageId=dockerImage }, " +
                "HostName: hostName, ContainerName: ContainerName { name=container }, minCpuCores: 1.0, " +
                "minDiskAvailableGb: 1.0, minMainMemoryAvailableGb: 1.0\n")) {
            Thread.sleep(10);
        }

        assertThat(DockerMock.getRequests(), startsWith("startContainer with DockerImage: DockerImage { imageId=dockerImage }, " +
                "HostName: hostName, ContainerName: ContainerName { name=container }, minCpuCores: 1.0, " +
                "minDiskAvailableGb: 1.0, minMainMemoryAvailableGb: 1.0\n"));


        // Check that NodeRepo has received the PATCH update
        while (!NodeRepoMock.getRequests().startsWith("updateNodeAttributes with HostName: hostName, " +
                "restartGeneration: 1, DockerImage: DockerImage { imageId=dockerImage }, containerVespaVersion: null\n")) {
            Thread.sleep(10);
        }

        assertThat(NodeRepoMock.getRequests(), startsWith("updateNodeAttributes with HostName: hostName, restartGeneration: 1," +
                " DockerImage: DockerImage { imageId=dockerImage }, containerVespaVersion: null\n"));

        // Force orchestrator to reject the suspend
        OrchestratorMock.setForceGroupSuspendResponse(Optional.of("Orchestrator reject suspend"));

        // At this point NodeAdmin should be fine with the suspend and it is up to Orchestrator
        while (!updater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED)
                .equals(Optional.of("Orchestrator reject suspend"))) {
            Thread.sleep(5);
        }
        assertThat(updater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED), is(Optional.of("Orchestrator reject suspend")));

        //Make orchestrator allow suspend requests
        OrchestratorMock.setForceGroupSuspendResponse(Optional.empty());
        assertThat(updater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED), is(Optional.empty()));

        // Now, change data in node repo, should not propagate.
        NodeRepoMock.clearContainerNodeSpecs();

        // New node repo state should have not propagated to node admin
        Thread.sleep(2);
        assertThat(nodeAdmin.getListOfHosts().size(), is(1));

        // Now resume
        assertThat(updater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED), is(Optional.empty()));

        // Now node repo state should propagate to node admin again
        while (nodeAdmin.getListOfHosts().size() != 0) {
            Thread.sleep(1);
        }

        final String[] allRequests = OrchestratorMock.getRequests().split("\n");
        final List<String> noRepeatingRequests = new ArrayList<>();
        for (String request : allRequests) {
            if (!noRepeatingRequests.contains(request)) {
                noRepeatingRequests.add(request);
            }
        }

        List<String> expectedRequests = Arrays.asList("Resume for hostName",
                "Suspend with parent: basehostname and hostnames: [hostName] - Forced response: Optional[Orchestrator reject suspend]",
                "Suspend with parent: basehostname and hostnames: [hostName] - Forced response: Optional.empty");

        // Check that the orchestrator did receive and properly responded to the previous requests
        assertThat(noRepeatingRequests, is(expectedRequests));

        updater.deconstruct();
    }
}

package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertThat;

/**
 * Test NodeState transitions in NodeRepository
 *
 * @author valerijf
 */

public class NodeStateTest {
    private NodeRepoMock nodeRepositoryMock;
    private DockerMock dockerMock;
    private HostName hostName;
    private ContainerNodeSpec initialContainerNodeSpec;
    private NodeAdminStateUpdater updater;

    @Before
    public void before() throws InterruptedException {
        try {
            OrchestratorMock.semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        OrchestratorMock.reset();
        NodeRepoMock.reset();
        DockerMock.reset();

        OrchestratorMock orchestratorMock = new OrchestratorMock();
        nodeRepositoryMock = new NodeRepoMock();
        dockerMock = new DockerMock();

        Function<HostName, NodeAgent> nodeAgentFactory = (hostName) ->
                new NodeAgentImpl(hostName, dockerMock, nodeRepositoryMock, orchestratorMock);
        NodeAdmin nodeAdmin = new NodeAdminImpl(dockerMock, nodeAgentFactory);

        hostName = new HostName("hostName");
        initialContainerNodeSpec = new ContainerNodeSpec(
                hostName,
                Optional.of(new DockerImage("dockerImage")),
                new ContainerName("container"),
                NodeState.ACTIVE,
                Optional.of(1L),
                Optional.of(1L),
                Optional.of(1d),
                Optional.of(1d),
                Optional.of(1d));
        NodeRepoMock.addContainerNodeSpec(initialContainerNodeSpec);

        updater = new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 1, orchestratorMock, "basehostname");

        // Wait for node admin to be notified with node repo state and the docker container has been started
        while (nodeAdmin.getListOfHosts().size() == 0) {
            Thread.sleep(10);
        }

        while (!DockerMock.getRequests().startsWith("startContainer with DockerImage: DockerImage { imageId=dockerImage }, " +
                "HostName: hostName, ContainerName: ContainerName { name=container }, minCpuCores: 1.0, minDiskAvailableGb: 1.0, " +
                "minMainMemoryAvailableGb: 1.0\nexecuteInContainer with ContainerName: ContainerName { name=container }, " +
                "args: [/usr/bin/env, test, -x, /opt/vespa/bin/vespa-nodectl]\nexecuteInContainer with ContainerName: " +
                "ContainerName { name=container }, args: [/opt/vespa/bin/vespa-nodectl, resume]\n")) {
            Thread.sleep(10);
        }
    }

    @After
    public void after() {
        updater.deconstruct();
        OrchestratorMock.semaphore.release();
    }


//    @Test
//    public void activeToDirty() throws InterruptedException, IOException {
//        // Change node state to dirty
//        NodeRepoMock.updateContainerNodeSpec(
//                initialContainerNodeSpec.hostname,
//                initialContainerNodeSpec.wantedDockerImage,
//                initialContainerNodeSpec.containerName,
//                NodeState.DIRTY,
//                initialContainerNodeSpec.wantedRestartGeneration,
//                initialContainerNodeSpec.currentRestartGeneration,
//                initialContainerNodeSpec.minCpuCores,
//                initialContainerNodeSpec.minMainMemoryAvailableGb,
//                initialContainerNodeSpec.minDiskAvailableGb);
//
//        // Wait until it is marked ready
//        Optional<ContainerNodeSpec> containerNodeSpec;
//        while ((containerNodeSpec = nodeRepositoryMock.getContainerNodeSpec(hostName)).isPresent()
//                && containerNodeSpec.get().nodeState != NodeState.READY) {
//            Thread.sleep(10);
//        }
//
//        assertThat(nodeRepositoryMock.getContainerNodeSpec(hostName).get().nodeState, is(NodeState.READY));
//
//
//        // Wait until docker receives deleteContainer request
//        String expectedDockerRequests = "stopContainer with ContainerName: ContainerName { name=container }\n" +
//                "deleteContainer with ContainerName: ContainerName { name=container }\n" +
//                "deleteApplicationStorage with ContainerName: ContainerName { name=container }\n";
//        while (!DockerMock.getRequests().endsWith(expectedDockerRequests)) {
//            Thread.sleep(10);
//        }
//
//        assertThat(DockerMock.getRequests(), endsWith(expectedDockerRequests));
//    }


    @Test
    public void activeToInactiveToActive() throws InterruptedException, IOException {
        String initialDockerRequests = DockerMock.getRequests() +
                "stopContainer with ContainerName: ContainerName { name=container }\n" +
                "deleteContainer with ContainerName: ContainerName { name=container }\n";
        Optional<DockerImage> newDockerImage = Optional.of(new DockerImage("newDockerImage"));

        // Change node state to inactive and change the wanted docker image
        NodeRepoMock.updateContainerNodeSpec(
                initialContainerNodeSpec.hostname,
                newDockerImage,
                initialContainerNodeSpec.containerName,
                NodeState.INACTIVE,
                initialContainerNodeSpec.wantedRestartGeneration,
                initialContainerNodeSpec.currentRestartGeneration,
                initialContainerNodeSpec.minCpuCores,
                initialContainerNodeSpec.minMainMemoryAvailableGb,
                initialContainerNodeSpec.minDiskAvailableGb);

        while (!initialDockerRequests.equals(DockerMock.getRequests())) {
            Thread.sleep(10);
        }
        assertThat(initialDockerRequests, is(DockerMock.getRequests()));


        // Change node state to active
        NodeRepoMock.updateContainerNodeSpec(
                initialContainerNodeSpec.hostname,
                newDockerImage,
                initialContainerNodeSpec.containerName,
                NodeState.ACTIVE,
                initialContainerNodeSpec.wantedRestartGeneration,
                initialContainerNodeSpec.currentRestartGeneration,
                initialContainerNodeSpec.minCpuCores,
                initialContainerNodeSpec.minMainMemoryAvailableGb,
                initialContainerNodeSpec.minDiskAvailableGb);

        while (DockerMock.getRequests().equals(initialDockerRequests)) {
            Thread.sleep(10);
        }
        assertThat(initialDockerRequests, not(DockerMock.getRequests()));
    }
}

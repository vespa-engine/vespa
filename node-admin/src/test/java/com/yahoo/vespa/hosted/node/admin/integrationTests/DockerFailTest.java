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

import java.util.Optional;
import java.util.function.Function;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
  * @author valerijf
 */
public class DockerFailTest {
    private NodeRepoMock nodeRepositoryMock;
    private DockerMock dockerMock;
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

        HostName hostName = new HostName("hostName");
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

    @Test
    public void dockerFailTest() throws InterruptedException {
        dockerMock.deleteContainer(initialContainerNodeSpec.containerName);

        String goal = "startContainer with DockerImage: DockerImage { imageId=dockerImage }, HostName: hostName, " +
                "ContainerName: ContainerName { name=container }, minCpuCores: 1.0, minDiskAvailableGb: 1.0, minMainMemoryAvailableGb: 1.0\n" +
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/usr/bin/env, test, -x, /opt/vespa/bin/vespa-nodectl]\n" +
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/opt/vespa/bin/vespa-nodectl, resume]\n" +
                "deleteContainer with ContainerName: ContainerName { name=container }\n" +
                "startContainer with DockerImage: DockerImage { imageId=dockerImage }, HostName: hostName, ContainerName: " +
                "ContainerName { name=container }, minCpuCores: 1.0, minDiskAvailableGb: 1.0, minMainMemoryAvailableGb: 1.0\n" +
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/usr/bin/env, test, -x, /opt/vespa/bin/vespa-nodectl]\n" +
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/opt/vespa/bin/vespa-nodectl, resume]\n";
        while (!DockerMock.getRequests().equals(goal)) {
            Thread.sleep(10);
        }

        assertThat(DockerMock.getRequests(), is(goal));
    }
}

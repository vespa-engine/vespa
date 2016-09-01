// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeState;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author valerijf
 */
public class MultiDockerTest {
    private NodeRepoMock nodeRepositoryMock;
    private DockerMock dockerMock;
    private NodeAdmin nodeAdmin;
    private NodeAdminStateUpdater updater;

    @Before
    public void before() throws InterruptedException {
        try {
            OrchestratorMock.semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        MaintenanceSchedulerMock.reset();
        OrchestratorMock.reset();
        NodeRepoMock.reset();
        DockerMock.reset();

        MaintenanceSchedulerMock maintenanceSchedulerMock = new MaintenanceSchedulerMock();
        OrchestratorMock orchestratorMock = new OrchestratorMock();
        nodeRepositoryMock = new NodeRepoMock();
        dockerMock = new DockerMock();

        Function<HostName, NodeAgent> nodeAgentFactory = (hostName) ->
                new NodeAgentImpl(hostName, nodeRepositoryMock, orchestratorMock, new DockerOperationsImpl(dockerMock), maintenanceSchedulerMock);
        nodeAdmin = new NodeAdminImpl(dockerMock, nodeAgentFactory, maintenanceSchedulerMock, 100);
        updater = new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 1, orchestratorMock, "basehostname");
    }

    @After
    public void after() {
        updater.deconstruct();
        OrchestratorMock.semaphore.release();
    }

    @Ignore // TODO: Remove
    @Test
    public void test() throws InterruptedException, IOException {
        addAndWaitForNode(new HostName("host1"), new ContainerName("container1"), Optional.of(new DockerImage("image1")));
        ContainerNodeSpec containerNodeSpec2 =
                addAndWaitForNode(new HostName("host2"), new ContainerName("container2"), Optional.of(new DockerImage("image2")));

        NodeRepoMock.updateContainerNodeSpec(
                containerNodeSpec2.hostname,
                containerNodeSpec2.wantedDockerImage,
                containerNodeSpec2.containerName,
                NodeState.DIRTY,
                containerNodeSpec2.wantedRestartGeneration,
                containerNodeSpec2.currentRestartGeneration,
                containerNodeSpec2.minCpuCores,
                containerNodeSpec2.minMainMemoryAvailableGb,
                containerNodeSpec2.minDiskAvailableGb);

        // Wait until it is marked ready
        Optional<ContainerNodeSpec> tempContainerNodeSpec;
        while ((tempContainerNodeSpec = nodeRepositoryMock.getContainerNodeSpec(containerNodeSpec2.hostname)).isPresent()
                && tempContainerNodeSpec.get().nodeState != NodeState.READY) {
            Thread.sleep(10);
        }

        addAndWaitForNode(new HostName("host3"), new ContainerName("container3"), Optional.of(new DockerImage("image1")));

        assertThat(DockerMock.getRequests(), is(
                "startContainer with DockerImage: DockerImage { imageId=image1 }, HostName: host1, ContainerName: ContainerName { name=container1 }, InetAddress: null, " +
                "minCpuCores: 1.0, minDiskAvailableGb: 1.0, minMainMemoryAvailableGb: 1.0\n" +
                "executeInContainer with ContainerName: ContainerName { name=container1 }, args: [/usr/bin/env, test, -x, /opt/vespa/bin/vespa-nodectl]\n" +
                "executeInContainer with ContainerName: ContainerName { name=container1 }, args: [/opt/vespa/bin/vespa-nodectl, resume]\n" +

                "startContainer with DockerImage: DockerImage { imageId=image2 }, HostName: host2, ContainerName: ContainerName { name=container2 }, InetAddress: null, " +
                "minCpuCores: 1.0, minDiskAvailableGb: 1.0, minMainMemoryAvailableGb: 1.0\n" +
                "executeInContainer with ContainerName: ContainerName { name=container2 }, args: [/usr/bin/env, test, -x, /opt/vespa/bin/vespa-nodectl]\n" +
                "executeInContainer with ContainerName: ContainerName { name=container2 }, args: [/opt/vespa/bin/vespa-nodectl, resume]\n" +

                "stopContainer with ContainerName: ContainerName { name=container2 }\n" +
                "deleteContainer with ContainerName: ContainerName { name=container2 }\n" +

                "startContainer with DockerImage: DockerImage { imageId=image1 }, HostName: host3, ContainerName: ContainerName { name=container3 }, InetAddress: null, " +
                "minCpuCores: 1.0, minDiskAvailableGb: 1.0, minMainMemoryAvailableGb: 1.0\n" +
                "executeInContainer with ContainerName: ContainerName { name=container3 }, args: [/usr/bin/env, test, -x, /opt/vespa/bin/vespa-nodectl]\n" +
                "executeInContainer with ContainerName: ContainerName { name=container3 }, args: [/opt/vespa/bin/vespa-nodectl, resume]\n"));

        assertThat(MaintenanceSchedulerMock.getRequests(), is("DeleteContainerStorage with ContainerName: ContainerName { name=container2 }\n"));

        String nodeRepoExpectedRequests =
                "updateNodeAttributes with HostName: host1, restartGeneration: 1, DockerImage: DockerImage { imageId=image1 }, containerVespaVersion: null\n" +
                "updateNodeAttributes with HostName: host2, restartGeneration: 1, DockerImage: DockerImage { imageId=image2 }, containerVespaVersion: null\n" +
                "markAsReady with HostName: host2\n" +
                "updateNodeAttributes with HostName: host3, restartGeneration: 1, DockerImage: DockerImage { imageId=image1 }, containerVespaVersion: null\n";

        while (!NodeRepoMock.getRequests().equals(nodeRepoExpectedRequests)) {
            Thread.sleep(10);
        }

        assertThat(NodeRepoMock.getRequests(), is(nodeRepoExpectedRequests));
    }

    private ContainerNodeSpec addAndWaitForNode(HostName hostName, ContainerName containerName, Optional<DockerImage> dockerImage) throws InterruptedException {
        ContainerNodeSpec containerNodeSpec = new ContainerNodeSpec(
                hostName,
                dockerImage,
                containerName,
                NodeState.ACTIVE,
                Optional.of(1L),
                Optional.of(1L),
                Optional.of(1d),
                Optional.of(1d),
                Optional.of(1d));
        NodeRepoMock.addContainerNodeSpec(containerNodeSpec);

        // Wait for node admin to be notified with node repo state and the docker container has been started
        while (nodeAdmin.getListOfHosts().size() != NodeRepoMock.getNumberOfContainerSpecs()) {
            Thread.sleep(10);
        }

        while (!DockerMock.getRequests().endsWith("startContainer with DockerImage: " + dockerImage.get() + ", " +
                "HostName: " + hostName + ", ContainerName: " + containerName + ", InetAddress: null, minCpuCores: 1.0, minDiskAvailableGb: 1.0, " +
                "minMainMemoryAvailableGb: 1.0\nexecuteInContainer with ContainerName: " + containerName + ", " +
                "args: [/usr/bin/env, test, -x, /opt/vespa/bin/vespa-nodectl]\nexecuteInContainer with ContainerName: " +
                containerName + ", args: [/opt/vespa/bin/vespa-nodectl, resume]\n")) {
            Thread.sleep(10);
        }

        return containerNodeSpec;
    }
}

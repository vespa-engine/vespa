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
        NodeAdmin nodeAdmin = new NodeAdminImpl(dockerMock, nodeAgentFactory, maintenanceSchedulerMock, 100);

        HostName hostName = new HostName("host1");
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

        while (!DockerMock.getRequests().startsWith(
                "createStartContainerCommand with DockerImage: DockerImage { imageId=dockerImage }, HostName: hostName, ContainerName: ContainerName { name=container }\n" +
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/usr/bin/env, test, -x, /opt/vespa/bin/vespa-nodectl]\n" +
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/opt/vespa/bin/vespa-nodectl, resume]\n")) {
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

        String goal = "createStartContainerCommand with DockerImage: DockerImage { imageId=dockerImage }, HostName: hostName, ContainerName: ContainerName { name=container }\n" +
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/usr/bin/env, test, -x, /opt/vespa/bin/vespa-nodectl]\n" +
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/opt/vespa/bin/vespa-nodectl, resume]\n" +
                "deleteContainer with ContainerName: ContainerName { name=container }\n" +
                "startContainer with DockerImage: DockerImage { imageId=dockerImage }, HostName: host1, ContainerName: " +
                "ContainerName { name=container }, InetAddress: null, minCpuCores: 1.0, minDiskAvailableGb: 1.0, minMainMemoryAvailableGb: 1.0\n" +
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/usr/bin/env, test, -x, /opt/vespa/bin/vespa-nodectl]\n" +
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/opt/vespa/bin/vespa-nodectl, resume]\n";


        while (!DockerMock.getRequests().equals(goal)) {
            Thread.sleep(1000);
        }

        assertThat(DockerMock.getRequests(), is(goal));
    }
}

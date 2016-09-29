// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author valerijf
 */
public class MultiDockerTest {
    private CallOrderVerifier callOrder;
    private NodeRepoMock nodeRepositoryMock;
    private DockerMock dockerMock;
    private NodeAdmin nodeAdmin;
    private NodeAdminStateUpdater updater;

    @Before
    public void before() throws InterruptedException, UnknownHostException {
        callOrder = new CallOrderVerifier();
        MaintenanceSchedulerMock maintenanceSchedulerMock = new MaintenanceSchedulerMock(callOrder);
        OrchestratorMock orchestratorMock = new OrchestratorMock(callOrder);
        nodeRepositoryMock = new NodeRepoMock(callOrder);
        dockerMock = new DockerMock(callOrder);

        Environment environment = mock(Environment.class);
        when(environment.getConfigServerHosts()).thenReturn(Collections.emptySet());
        when(environment.getInetAddressForHost(any(String.class))).thenReturn(InetAddress.getByName("1.1.1.1"));

        Function<String, NodeAgent> nodeAgentFactory = (hostName) ->
                new NodeAgentImpl(hostName, nodeRepositoryMock, orchestratorMock, new DockerOperationsImpl(dockerMock, environment), maintenanceSchedulerMock);
        nodeAdmin = new NodeAdminImpl(dockerMock, nodeAgentFactory, maintenanceSchedulerMock, 100,
                new MetricReceiverWrapper(MetricReceiver.nullImplementation));
        updater = new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 1, orchestratorMock, "basehostname");
    }

    @After
    public void after() {
        updater.deconstruct();
    }

    @Test
    public void test() throws InterruptedException, IOException {
        addAndWaitForNode("host1", new ContainerName("container1"), Optional.of(new DockerImage("image1")));
        ContainerNodeSpec containerNodeSpec2 =
                addAndWaitForNode("host2", new ContainerName("container2"), Optional.of(new DockerImage("image2")));

        nodeRepositoryMock.updateContainerNodeSpec(
                containerNodeSpec2.hostname,
                containerNodeSpec2.wantedDockerImage,
                containerNodeSpec2.containerName,
                Node.State.dirty,
                containerNodeSpec2.wantedRestartGeneration,
                containerNodeSpec2.currentRestartGeneration,
                containerNodeSpec2.minCpuCores,
                containerNodeSpec2.minMainMemoryAvailableGb,
                containerNodeSpec2.minDiskAvailableGb);

        // Wait until it is marked ready
        Optional<ContainerNodeSpec> tempContainerNodeSpec;
        while ((tempContainerNodeSpec = nodeRepositoryMock.getContainerNodeSpec(containerNodeSpec2.hostname)).isPresent()
                && tempContainerNodeSpec.get().nodeState != Node.State.ready) {
            Thread.sleep(10);
        }

        addAndWaitForNode("host3", new ContainerName("container3"), Optional.of(new DockerImage("image1")));

        assertTrue(callOrder.verifyInOrder(1000,
                "createContainerCommand with DockerImage: DockerImage { imageId=image1 }, HostName: host1, ContainerName: ContainerName { name=container1 }",
                "executeInContainer with ContainerName: ContainerName { name=container1 }, args: [/usr/bin/env, test, -x, /opt/yahoo/vespa/bin/vespa-nodectl]",
                "executeInContainer with ContainerName: ContainerName { name=container1 }, args: [/opt/yahoo/vespa/bin/vespa-nodectl, resume]",

                "createContainerCommand with DockerImage: DockerImage { imageId=image2 }, HostName: host2, ContainerName: ContainerName { name=container2 }",
                "executeInContainer with ContainerName: ContainerName { name=container2 }, args: [/usr/bin/env, test, -x, /opt/yahoo/vespa/bin/vespa-nodectl]",
                "executeInContainer with ContainerName: ContainerName { name=container2 }, args: [/opt/yahoo/vespa/bin/vespa-nodectl, resume]",

                "stopContainer with ContainerName: ContainerName { name=container2 }",
                "deleteContainer with ContainerName: ContainerName { name=container2 }",

                "createContainerCommand with DockerImage: DockerImage { imageId=image1 }, HostName: host3, ContainerName: ContainerName { name=container3 }",
                "executeInContainer with ContainerName: ContainerName { name=container3 }, args: [/usr/bin/env, test, -x, /opt/yahoo/vespa/bin/vespa-nodectl]",
                "executeInContainer with ContainerName: ContainerName { name=container3 }, args: [/opt/yahoo/vespa/bin/vespa-nodectl, resume]"));

        assertTrue("Maintainer did not receive call to delete application storage", callOrder.verifyInOrder(1000,
                        "deleteContainer with ContainerName: ContainerName { name=container2 }",
                        "DeleteContainerStorage with ContainerName: ContainerName { name=container2 }"));

        assertTrue(callOrder.verifyInOrder(1000,
                "updateNodeAttributes with HostName: host1, NodeAttributes: NodeAttributes{restartGeneration=1, dockerImage=DockerImage { imageId=image1 }, vespaVersion='null'}",
                "updateNodeAttributes with HostName: host2, NodeAttributes: NodeAttributes{restartGeneration=1, dockerImage=DockerImage { imageId=image2 }, vespaVersion='null'}",
                "markAsReady with HostName: host2",
                "updateNodeAttributes with HostName: host3, NodeAttributes: NodeAttributes{restartGeneration=1, dockerImage=DockerImage { imageId=image1 }, vespaVersion='null'}"));
    }

    private ContainerNodeSpec addAndWaitForNode(String hostName, ContainerName containerName, Optional<DockerImage> dockerImage) throws InterruptedException {
        ContainerNodeSpec containerNodeSpec = new ContainerNodeSpec(
                hostName,
                dockerImage,
                containerName,
                Node.State.active,
                "tenant",
                "docker",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(1L),
                Optional.of(1L),
                Optional.of(1d),
                Optional.of(1d),
                Optional.of(1d));
        nodeRepositoryMock.addContainerNodeSpec(containerNodeSpec);

        // Wait for node admin to be notified with node repo state and the docker container has been started
        while (nodeAdmin.getListOfHosts().size() != nodeRepositoryMock.getNumberOfContainerSpecs()) {
            Thread.sleep(10);
        }

        assert callOrder.verifyInOrder(1000,
                "createContainerCommand with DockerImage: " + dockerImage.get() + ", HostName: " + hostName + ", ContainerName: " + containerName,
                "executeInContainer with ContainerName: " + containerName + ", args: [/usr/bin/env, test, -x, /opt/yahoo/vespa/bin/vespa-nodectl]",
                "executeInContainer with ContainerName: " + containerName + ", args: [/opt/yahoo/vespa/bin/vespa-nodectl, resume]");

        return containerNodeSpec;
    }
}

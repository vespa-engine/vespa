// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.InetAddressResolver;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
  * @author valerijf
 */
public class DockerFailTest {
    private CallOrderVerifier callOrderVerifier;
    private Docker dockerMock;
    private NodeAdminStateUpdater updater;
    private ContainerNodeSpec initialContainerNodeSpec;

    @Before
    public void before() throws InterruptedException, UnknownHostException {
        callOrderVerifier = new CallOrderVerifier();
        StorageMaintainerMock maintenanceSchedulerMock = new StorageMaintainerMock(callOrderVerifier);
        OrchestratorMock orchestratorMock = new OrchestratorMock(callOrderVerifier);
        NodeRepoMock nodeRepositoryMock = new NodeRepoMock(callOrderVerifier);
        dockerMock = new DockerMock(callOrderVerifier);

        InetAddressResolver inetAddressResolver = mock(InetAddressResolver.class);
        when(inetAddressResolver.getInetAddressForHost(any(String.class))).thenReturn(InetAddress.getByName("1.1.1.1"));
        Environment environment = new Environment(Collections.emptySet(),
                                                  "dev",
                                                  "us-east-1",
                                                  inetAddressResolver);

        MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        Function<String, NodeAgent> nodeAgentFactory = (hostName) -> {
            final Maintainer maintainer = new Maintainer();
            return new NodeAgentImpl(hostName, nodeRepositoryMock, orchestratorMock,
                                     new DockerOperationsImpl(dockerMock, environment, maintainer),
                                     maintenanceSchedulerMock, mr, environment, maintainer);
        };
        NodeAdmin nodeAdmin = new NodeAdminImpl(dockerMock, nodeAgentFactory, maintenanceSchedulerMock, 100, mr);

        initialContainerNodeSpec = new ContainerNodeSpec(
                "hostName",
                Optional.of(new DockerImage("dockerImage")),
                new ContainerName("container"),
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
        nodeRepositoryMock.addContainerNodeSpec(initialContainerNodeSpec);

        updater = new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 1, orchestratorMock, "basehostname");

        // Wait for node admin to be notified with node repo state and the docker container has been started
        while (nodeAdmin.getListOfHosts().size() == 0) {
            Thread.sleep(10);
        }

        callOrderVerifier.assertInOrder(
                "createContainerCommand with DockerImage: DockerImage { imageId=dockerImage }, HostName: hostName, ContainerName: ContainerName { name=container }",
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/usr/bin/env, test, -x, /opt/yahoo/vespa/bin/vespa-nodectl]",
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/opt/yahoo/vespa/bin/vespa-nodectl, resume]");
    }

    @After
    public void after() {
        updater.deconstruct();
    }

    @Test
    public void dockerFailTest() throws InterruptedException {
        dockerMock.deleteContainer(initialContainerNodeSpec.containerName);

        callOrderVerifier.assertInOrder(
                "deleteContainer with ContainerName: ContainerName { name=container }",
                "createContainerCommand with DockerImage: DockerImage { imageId=dockerImage }, HostName: hostName, ContainerName: ContainerName { name=container }",
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/usr/bin/env, test, -x, /opt/yahoo/vespa/bin/vespa-nodectl]",
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/opt/yahoo/vespa/bin/vespa-nodectl, resume]");
    }
}

// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
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
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that different wanted and current restart generation leads to execution of restart command
 *
 * @author musum
 */
public class RestartTest {

    @Test
    public void test() throws InterruptedException, UnknownHostException {
        CallOrderVerifier callOrderVerifier = new CallOrderVerifier();
        NodeRepoMock nodeRepositoryMock = new NodeRepoMock(callOrderVerifier);
        StorageMaintainerMock maintenanceSchedulerMock = new StorageMaintainerMock(callOrderVerifier);
        OrchestratorMock orchestratorMock = new OrchestratorMock(callOrderVerifier);
        DockerMock dockerMock = new DockerMock(callOrderVerifier);

        InetAddressResolver inetAddressResolver = mock(InetAddressResolver.class);
        when(inetAddressResolver.getInetAddressForHost(any(String.class))).thenReturn(InetAddress.getByName("1.1.1.1"));
        Environment environment = new Environment(Collections.emptySet(),
                                                  "dev",
                                                  "us-east-1",
                                                  "parent.host.name.yahoo.com",
                                                  inetAddressResolver);

        MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        Function<String, NodeAgent> nodeAgentFactory = (hostName) -> {
            final Maintainer maintainer = new Maintainer();
            return new NodeAgentImpl(hostName, nodeRepositoryMock, orchestratorMock,
                                     new DockerOperationsImpl(dockerMock, environment, maintainer),
                                     maintenanceSchedulerMock, mr, environment, maintainer);
        };
        NodeAdmin nodeAdmin = new NodeAdminImpl(dockerMock, nodeAgentFactory, maintenanceSchedulerMock, 100, mr);

        long wantedRestartGeneration = 1;
        long currentRestartGeneration = wantedRestartGeneration;
        nodeRepositoryMock.addContainerNodeSpec(createContainerNodeSpec(wantedRestartGeneration, currentRestartGeneration));

        NodeAdminStateUpdater updater = new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 1, orchestratorMock, "basehostname");

        // Wait for node admin to be notified with node repo state and the docker container has been started
        while (nodeAdmin.getListOfHosts().size() == 0) {
            Thread.sleep(10);
        }

        // Check that the container is started and NodeRepo has received the PATCH update
        callOrderVerifier.assertInOrder("createContainerCommand with DockerImage: DockerImage { imageId=dockerImage }, HostName: host1, ContainerName: ContainerName { name=container }",
                                        "updateNodeAttributes with HostName: host1, NodeAttributes: NodeAttributes{restartGeneration=1, dockerImage=DockerImage { imageId=dockerImage }, vespaVersion='null'}");

        wantedRestartGeneration = 2;
        currentRestartGeneration = 1;
        nodeRepositoryMock.updateContainerNodeSpec(createContainerNodeSpec(wantedRestartGeneration, currentRestartGeneration));

        callOrderVerifier.assertInOrder("Suspend for host1",
                                        "executeInContainer with ContainerName: ContainerName { name=container }, args: [/opt/yahoo/vespa/bin/vespa-nodectl, restart]");
        updater.deconstruct();
    }

    private ContainerNodeSpec createContainerNodeSpec(long wantedRestartGeneration, long currentRestartGeneration) {
        return new ContainerNodeSpec("host1",
                                     Optional.of(new DockerImage("dockerImage")),
                                     new ContainerName("container"),
                                     Node.State.active,
                                     "tenant",
                                     "docker",
                                     Optional.empty(),
                                     Optional.empty(),
                                     Optional.empty(),
                                     Optional.of(wantedRestartGeneration),
                                     Optional.of(currentRestartGeneration),
                                     Optional.of(1d),
                                     Optional.of(1d),
                                     Optional.of(1d));
    }
}

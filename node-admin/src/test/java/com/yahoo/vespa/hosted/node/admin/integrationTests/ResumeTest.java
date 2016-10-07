// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Scenario test for NodeAdminStateUpdater.
 *
 * @author dybis
 */
public class ResumeTest {
    @Test
    public void test() throws InterruptedException, UnknownHostException {
        CallOrderVerifier callOrderVerifier = new CallOrderVerifier();
        NodeRepoMock nodeRepositoryMock = new NodeRepoMock(callOrderVerifier);
        StorageMaintainerMock maintenanceSchedulerMock = new StorageMaintainerMock(callOrderVerifier);
        OrchestratorMock orchestratorMock = new OrchestratorMock(callOrderVerifier);
        DockerMock dockerMock = new DockerMock(callOrderVerifier);

        Environment environment = mock(Environment.class);
        when(environment.getConfigServerHosts()).thenReturn(Collections.emptySet());
        when(environment.getInetAddressForHost(any(String.class))).thenReturn(InetAddress.getByName("1.1.1.1"));

        MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        Function<String, NodeAgent> nodeAgentFactory = (hostName) ->
                new NodeAgentImpl(hostName, nodeRepositoryMock, orchestratorMock, new DockerOperationsImpl(dockerMock, environment), maintenanceSchedulerMock, mr);
        NodeAdmin nodeAdmin = new NodeAdminImpl(dockerMock, nodeAgentFactory, maintenanceSchedulerMock, 100, mr);

        nodeRepositoryMock.addContainerNodeSpec(new ContainerNodeSpec(
                "host1",
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
                Optional.of(1d)));

        NodeAdminStateUpdater updater = new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 1, orchestratorMock, "basehostname");

        // Wait for node admin to be notified with node repo state and the docker container has been started
        while (nodeAdmin.getListOfHosts().size() == 0) {
            Thread.sleep(10);
        }

        // Check that the container is started and NodeRepo has received the PATCH update
        callOrderVerifier.assertInOrder("createContainerCommand with DockerImage: DockerImage { imageId=dockerImage }, HostName: host1, ContainerName: ContainerName { name=container }",
                                        "updateNodeAttributes with HostName: host1, NodeAttributes: NodeAttributes{restartGeneration=1, dockerImage=DockerImage { imageId=dockerImage }, vespaVersion='null'}");

        // Force orchestrator to reject the suspend
        orchestratorMock.setForceGroupSuspendResponse(Optional.of("Orchestrator reject suspend"));

        // At this point NodeAdmin should be fine with the suspend and it is up to Orchestrator
        while (!updater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED)
                .equals(Optional.of("Orchestrator reject suspend"))) {
            Thread.sleep(10);
        }
        assertThat(updater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED), is(Optional.of("Orchestrator reject suspend")));

        //Make orchestrator allow suspend callOrderVerifier
        orchestratorMock.setForceGroupSuspendResponse(Optional.empty());
        assertThat(updater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED), is(Optional.empty()));

        // Now, change data in node repo, should not propagate.
        nodeRepositoryMock.clearContainerNodeSpecs();

        // New node repo state should have not propagated to node admin
        Thread.sleep(10);
        assertThat(nodeAdmin.getListOfHosts().size(), is(1));

        // Now resume
        assertThat(updater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED), is(Optional.empty()));

        // Now node repo state should propagate to node admin again
        while (nodeAdmin.getListOfHosts().size() != 0) {
            Thread.sleep(10);
        }

        callOrderVerifier.assertInOrder("Resume for host1",
                                        "Suspend with parent: basehostname and hostnames: [host1] - Forced response: Optional[Orchestrator reject suspend]",
                                        "Suspend with parent: basehostname and hostnames: [host1] - Forced response: Optional.empty");

        updater.deconstruct();
    }
}

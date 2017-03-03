// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * Scenario test for NodeAdminStateUpdater.
 *
 * @author dybis
 */
public class ResumeTest {
    @Test
    public void test() throws InterruptedException, UnknownHostException {
        try (DockerTester dockerTester = new DockerTester()) {
            final NodeAdmin nodeAdmin = dockerTester.getNodeAdmin();
            final OrchestratorMock orchestratorMock = dockerTester.getOrchestratorMock();
            final NodeAdminStateUpdater nodeAdminStateUpdater = dockerTester.getNodeAdminStateUpdater();

            dockerTester.addContainerNodeSpec(new ContainerNodeSpec.Builder()
                                                      .hostname("host1.test.yahoo.com")
                                                      .wantedDockerImage(new DockerImage("dockerImage"))
                                                      .nodeState(Node.State.active)
                                                      .nodeType("tenant")
                                                      .nodeFlavor("docker")
                                                      .wantedRestartGeneration(1L)
                                                      .currentRestartGeneration(1L)
                                                      .build());

            // Wait for node admin to be notified with node repo state and the docker container has been started

            while (nodeAdmin.getListOfHosts().size() == 0) {
                Thread.sleep(10);
            }

            CallOrderVerifier callOrderVerifier = dockerTester.getCallOrderVerifier();
            // Check that the container is started and NodeRepo has received the PATCH update
            callOrderVerifier.assertInOrder("createContainerCommand with DockerImage { imageId=dockerImage }, HostName: host1.test.yahoo.com, ContainerName { name=host1 }",
                                            "updateNodeAttributes with HostName: host1.test.yahoo.com, NodeAttributes{restartGeneration=1, rebootGeneration=0, dockerImage=dockerImage, vespaVersion=''}");

            // Force orchestrator to reject the suspend
            orchestratorMock.setForceGroupSuspendResponse(Optional.of("Orchestrator reject suspend"));

            // At this point NodeAdmin should be fine with the suspend and it is up to Orchestrator
            while (!nodeAdminStateUpdater
                    .setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED)
                    .equals(Optional.of("Orchestrator reject suspend"))) {
                Thread.sleep(10);
            }
            assertThat(nodeAdminStateUpdater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED), is(Optional.of("Orchestrator reject suspend")));

            //Make orchestrator allow suspend requests
            orchestratorMock.setForceGroupSuspendResponse(Optional.empty());
            assertThat(nodeAdminStateUpdater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED), is(Optional.empty()));

            // Now, change data in node repo, should not propagate.
            dockerTester.clearContainerNodeSpecs();

            // New node repo state should have not propagated to node admin
            Thread.sleep(10);
            assertThat(nodeAdmin.getListOfHosts().size(), is(1));

            // Now resume
            assertThat(nodeAdminStateUpdater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED), is(Optional.empty()));

            // Now node repo state should propagate to node admin again
            while (nodeAdmin.getListOfHosts().size() != 0) {
                Thread.sleep(10);
            }

            callOrderVerifier.assertInOrder("Resume for host1.test.yahoo.com",
                                            "Suspend with parent: basehostname and hostnames: [host1.test.yahoo.com] - Forced response: Optional[Orchestrator reject suspend]",
                                            "Suspend with parent: basehostname and hostnames: [host1.test.yahoo.com] - Forced response: Optional.empty");

        }
    }
}

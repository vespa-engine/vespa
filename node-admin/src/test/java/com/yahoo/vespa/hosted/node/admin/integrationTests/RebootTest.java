// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Ignore;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests rebooting of Docker host
 *
 * @author musum
 */
public class RebootTest {

    @Test
    @Ignore
    public void test() throws InterruptedException, UnknownHostException {
        try (DockerTester dockerTester = new DockerTester()) {

            dockerTester.addContainerNodeSpec(createContainerNodeSpec());

            // Wait for node admin to be notified with node repo state and the docker container has been started
            while (dockerTester.getNodeAdmin().getListOfHosts().size() == 0) {
                Thread.sleep(10);
            }

            CallOrderVerifier callOrderVerifier = dockerTester.getCallOrderVerifier();
            // Check that the container is started and NodeRepo has received the PATCH update
            callOrderVerifier.assertInOrder("createContainerCommand with DockerImage: DockerImage { imageId=dockerImage }, HostName: host1, ContainerName: ContainerName { name=container }",
                                            "updateNodeAttributes with HostName: host1, NodeAttributes: NodeAttributes{restartGeneration=1, dockerImage=DockerImage { imageId=dockerImage }, vespaVersion='null'}");

            NodeAdminStateUpdater updater = dockerTester.getNodeAdminStateUpdater();
            assertThat(updater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED),
                       is(Optional.of("Not all node agents are frozen.")));

            updater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED);

            NodeAdmin nodeAdmin = dockerTester.getNodeAdmin();
            // Wait for node admin to be frozen
            while ( ! nodeAdmin.isFrozen()) {
                System.out.println("Node admin not frozen yet");
                Thread.sleep(10);
            }

            assertTrue(nodeAdmin.freezeNodeAgentsAndCheckIfAllFrozen());

            callOrderVerifier.assertInOrder("executeInContainer with ContainerName: ContainerName { name=container }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", stop]");
        }
    }

    private ContainerNodeSpec createContainerNodeSpec() {
        return new ContainerNodeSpec.Builder()
                .hostname("host1")
                .wantedDockerImage(Optional.of(new DockerImage("dockerImage")))
                .containerName(new ContainerName("container"))
                .nodeState(Node.State.active)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .vespaVersion(Optional.of("6.50.0"))
                .wantedRestartGeneration(Optional.of(1L))
                .currentRestartGeneration(Optional.of(1L))
                .build();
    }
}

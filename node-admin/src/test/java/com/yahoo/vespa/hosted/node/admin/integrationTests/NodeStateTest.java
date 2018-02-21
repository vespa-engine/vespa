// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.io.IOException;

/**
 * Test NodeState transitions in NodeRepository
 *
 * @author freva
 */
public class NodeStateTest {
    private final ContainerNodeSpec initialContainerNodeSpec = new ContainerNodeSpec.Builder()
            .hostname("host1.test.yahoo.com")
            .wantedDockerImage(new DockerImage("dockerImage"))
            .nodeState(Node.State.active)
            .nodeType("tenant")
            .nodeFlavor("docker")
            .wantedRestartGeneration(1L)
            .currentRestartGeneration(1L)
            .minCpuCores(1)
            .minMainMemoryAvailableGb(1)
            .minDiskAvailableGb(1)
            .build();

    private void setup(DockerTester tester) throws InterruptedException {
        tester.addContainerNodeSpec(initialContainerNodeSpec);

        // Wait for node admin to be notified with node repo state and the docker container has been started
        while (tester.nodeAdmin.getListOfHosts().size() == 0) {
            Thread.sleep(10);
        }

        tester.callOrderVerifier.assertInOrder(
                "createContainerCommand with DockerImage { imageId=dockerImage }, HostName: host1.test.yahoo.com, ContainerName { name=host1 }",
                "executeInContainerAsRoot with ContainerName { name=host1 }, args: [" + DockerTester.NODE_PROGRAM + ", resume]");
    }


    @Test
    public void activeToDirty() throws InterruptedException, IOException {
        try (DockerTester dockerTester = new DockerTester()) {
            setup(dockerTester);
            // Change node state to dirty
            dockerTester.addContainerNodeSpec(new ContainerNodeSpec.Builder(initialContainerNodeSpec)
                    .nodeState(Node.State.dirty)
                    .minCpuCores(1)
                    .minMainMemoryAvailableGb(1)
                    .minDiskAvailableGb(1)
                    .build());

            // Wait until it is marked ready
            while (dockerTester.nodeRepositoryMock.getContainerNodeSpec(initialContainerNodeSpec.hostname)
                    .filter(nodeSpec -> nodeSpec.nodeState != Node.State.ready).isPresent()) {
                Thread.sleep(10);
            }

            dockerTester.callOrderVerifier.assertInOrder(
                    "executeInContainerAsRoot with ContainerName { name=host1 }, args: [" + DockerTester.NODE_PROGRAM + ", stop]",
                    "stopContainer with ContainerName { name=host1 }",
                    "deleteContainer with ContainerName { name=host1 }");
        }
    }

    @Test
    public void activeToInactiveToActive() throws InterruptedException, IOException {

        try (DockerTester dockerTester = new DockerTester()) {
            setup(dockerTester);

            DockerImage newDockerImage = new DockerImage("newDockerImage");

            // Change node state to inactive and change the wanted docker image
            dockerTester.addContainerNodeSpec(new ContainerNodeSpec.Builder(initialContainerNodeSpec)
                    .wantedDockerImage(newDockerImage)
                    .nodeState(Node.State.inactive)
                    .minCpuCores(1)
                    .minMainMemoryAvailableGb(1)
                    .minDiskAvailableGb(1)
                    .build());

            dockerTester.callOrderVerifier.assertInOrderWithAssertMessage(
                    "Node set to inactive, but no stop/delete call received",
                    "stopContainer with ContainerName { name=host1 }",
                    "deleteContainer with ContainerName { name=host1 }");


            // Change node state to active
            dockerTester.addContainerNodeSpec(new ContainerNodeSpec.Builder(initialContainerNodeSpec)
                    .wantedDockerImage(newDockerImage)
                    .nodeState(Node.State.active)
                    .minCpuCores(1)
                    .minMainMemoryAvailableGb(1)
                    .minDiskAvailableGb(1)
                    .build());

            // Check that the container is started again after the delete call
            dockerTester.callOrderVerifier.assertInOrderWithAssertMessage(
                    "Node not started again after being put to active state",
                    "deleteContainer with ContainerName { name=host1 }",
                    "createContainerCommand with DockerImage { imageId=newDockerImage }, HostName: host1.test.yahoo.com, ContainerName { name=host1 }",
                    "executeInContainerAsRoot with ContainerName { name=host1 }, args: [" + DockerTester.NODE_PROGRAM + ", resume]");
        }
    }
}

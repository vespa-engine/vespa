// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * Test NodeState transitions in NodeRepository
 *
 * @author freva
 */
public class NodeStateTest {
    private static ContainerNodeSpec initialContainerNodeSpec = new ContainerNodeSpec.Builder()
            .hostname("host1")
            .wantedDockerImage(new DockerImage("dockerImage"))
            .containerName(new ContainerName("container"))
            .nodeState(Node.State.active)
            .nodeType("tenant")
            .nodeFlavor("docker")
            .wantedRestartGeneration(1L)
            .currentRestartGeneration(1L)
            .build();

    private void setup(DockerTester tester) throws InterruptedException {
        tester.addContainerNodeSpec(initialContainerNodeSpec);

        // Wait for node admin to be notified with node repo state and the docker container has been started
        while (tester.getNodeAdmin().getListOfHosts().size() == 0) {
            Thread.sleep(10);
        }

        tester.getCallOrderVerifier().assertInOrder(
                "createContainerCommand with DockerImage: DockerImage { imageId=dockerImage }, HostName: host1, ContainerName: ContainerName { name=container }",
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [/usr/bin/env, test, -x, " + DockerOperationsImpl.NODE_PROGRAM + "]",
                "executeInContainer with ContainerName: ContainerName { name=container }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]");
    }


    @Test
    public void activeToDirty() throws InterruptedException, IOException {
        try (DockerTester dockerTester = new DockerTester()) {
            setup(dockerTester);
            // Change node state to dirty
            dockerTester.updateContainerNodeSpec(new ContainerNodeSpec.Builder(initialContainerNodeSpec)
                    .nodeState(Node.State.dirty)
                    .build());

            // Wait until it is marked ready
            Optional<ContainerNodeSpec> containerNodeSpec;
            while ((containerNodeSpec = dockerTester.getContainerNodeSpec(initialContainerNodeSpec.hostname)).isPresent()
                    && containerNodeSpec.get().nodeState != Node.State.ready) {
                Thread.sleep(10);
            }

            assertThat(dockerTester.getContainerNodeSpec(initialContainerNodeSpec.hostname)
                                   .get().nodeState, is(Node.State.ready));

            dockerTester.getCallOrderVerifier()
                        .assertInOrder("executeInContainer with ContainerName: ContainerName { name=container }, args: [/usr/bin/env, test, -x, " + DockerOperationsImpl.NODE_PROGRAM + "]",
                                       "executeInContainer with ContainerName: ContainerName { name=container }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", stop]",
                                       "stopContainer with ContainerName: ContainerName { name=container }",
                                       "deleteContainer with ContainerName: ContainerName { name=container }");
        }
    }

    @Test
    public void activeToInactiveToActive() throws InterruptedException, IOException {

        try (DockerTester dockerTester = new DockerTester()) {
            setup(dockerTester);

            DockerImage newDockerImage = new DockerImage("newDockerImage");

            // Change node state to inactive and change the wanted docker image
            dockerTester.updateContainerNodeSpec(new ContainerNodeSpec.Builder(initialContainerNodeSpec)
                    .wantedDockerImage(newDockerImage)
                    .nodeState(Node.State.inactive)
                    .build());

            CallOrderVerifier callOrderVerifier = dockerTester.getCallOrderVerifier();
            callOrderVerifier.assertInOrderWithAssertMessage("Node set to inactive, but no stop/delete call received",
                                                             "stopContainer with ContainerName: ContainerName { name=container }",
                                                             "deleteContainer with ContainerName: ContainerName { name=container }");


            // Change node state to active
            dockerTester.updateContainerNodeSpec(new ContainerNodeSpec.Builder(initialContainerNodeSpec)
                    .wantedDockerImage(newDockerImage)
                    .nodeState(Node.State.active)
                    .build());

            // Check that the container is started again after the delete call
            callOrderVerifier.assertInOrderWithAssertMessage("Node not started again after being put to active state",
                                                             "deleteContainer with ContainerName: ContainerName { name=container }",
                                                             "createContainerCommand with DockerImage: DockerImage { imageId=newDockerImage }, HostName: host1, ContainerName: ContainerName { name=container }",
                                                             "executeInContainer with ContainerName: ContainerName { name=container }, args: [/usr/bin/env, test, -x, " + DockerOperationsImpl.NODE_PROGRAM + "]",
                                                             "executeInContainer with ContainerName: ContainerName { name=container }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]");
        }
    }
}

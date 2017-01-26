// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.io.IOException;

/**
 * @author freva
 */
public class MultiDockerTest {

    @Test
    public void test() throws InterruptedException, IOException {
        try (DockerTester dockerTester = new DockerTester()) {
            addAndWaitForNode(dockerTester, "host1", new ContainerName("container1"), new DockerImage("image1"));
            ContainerNodeSpec containerNodeSpec2 =
                    addAndWaitForNode(dockerTester, "host2", new ContainerName("container2"), new DockerImage("image2"));

            dockerTester.updateContainerNodeSpec(
                    new ContainerNodeSpec.Builder(containerNodeSpec2)
                            .nodeState(Node.State.dirty)
                            .build());

            // Wait until it is marked ready
            while (dockerTester.getContainerNodeSpec(containerNodeSpec2.hostname)
                    .filter(nodeSpec -> nodeSpec.nodeState != Node.State.ready).isPresent()) {
                Thread.sleep(10);
            }

            addAndWaitForNode(dockerTester, "host3", new ContainerName("container3"), new DockerImage("image1"));

            CallOrderVerifier callOrderVerifier = dockerTester.getCallOrderVerifier();
            callOrderVerifier.assertInOrder(
                    "createContainerCommand with DockerImage { imageId=image1 }, HostName: host1, ContainerName { name=container1 }",
                    "executeInContainer with ContainerName { name=container1 }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]",

                    "createContainerCommand with DockerImage { imageId=image2 }, HostName: host2, ContainerName { name=container2 }",
                    "executeInContainer with ContainerName { name=container2 }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]",

                    "stopContainer with ContainerName { name=container2 }",
                    "deleteContainer with ContainerName { name=container2 }",

                    "createContainerCommand with DockerImage { imageId=image1 }, HostName: host3, ContainerName { name=container3 }",
                    "executeInContainer with ContainerName { name=container3 }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]");

            callOrderVerifier.assertInOrderWithAssertMessage("Maintainer did not receive call to delete application storage",
                                                             "deleteContainer with ContainerName { name=container2 }",
                                                             "DeleteContainerStorage with ContainerName { name=container2 }");

            callOrderVerifier.assertInOrder(
                    "updateNodeAttributes with HostName: host1, NodeAttributes{restartGeneration=1, rebootGeneration=0, dockerImage=image1, vespaVersion=''}",
                    "updateNodeAttributes with HostName: host2, NodeAttributes{restartGeneration=1, rebootGeneration=0, dockerImage=image2, vespaVersion=''}",
                    "markAsReady with HostName: host2",
                    "updateNodeAttributes with HostName: host3, NodeAttributes{restartGeneration=1, rebootGeneration=0, dockerImage=image1, vespaVersion=''}");
        }
    }

    private ContainerNodeSpec addAndWaitForNode(DockerTester tester, String hostName, ContainerName containerName, DockerImage dockerImage) throws InterruptedException {
        ContainerNodeSpec containerNodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .wantedDockerImage(dockerImage)
                .containerName(containerName)
                .nodeState(Node.State.active)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .wantedRestartGeneration(1L)
                .currentRestartGeneration(1L)
                .build();

        tester.addContainerNodeSpec(containerNodeSpec);

        // Wait for node admin to be notified with node repo state and the docker container has been started
        while (tester.getNodeAdmin().getListOfHosts().size() != tester.getNumberOfContainerSpecs()) {
            Thread.sleep(10);
        }

        tester.getCallOrderVerifier().assertInOrder(
                "createContainerCommand with " + dockerImage + ", HostName: " + hostName + ", " + containerName,
                "executeInContainer with " + containerName + ", args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]");

        return containerNodeSpec;
    }
}

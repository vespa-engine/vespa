// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

/**
 * @author valerijf
 */
public class MultiDockerTest {

    @Test
    public void test() throws InterruptedException, IOException {
        try (DockerTester dockerTester = new DockerTester()) {
            addAndWaitForNode(dockerTester, "host1", new ContainerName("container1"), Optional.of(new DockerImage("image1")));
            ContainerNodeSpec containerNodeSpec2 =
                    addAndWaitForNode(dockerTester, "host2", new ContainerName("container2"), Optional.of(new DockerImage("image2")));

            dockerTester.updateContainerNodeSpec(
                    containerNodeSpec2.hostname,
                    containerNodeSpec2.wantedDockerImage,
                    containerNodeSpec2.containerName,
                    Node.State.dirty,
                    containerNodeSpec2.nodeType,
                    containerNodeSpec2.nodeFlavor,
                    containerNodeSpec2.wantedRestartGeneration,
                    containerNodeSpec2.currentRestartGeneration,
                    containerNodeSpec2.minCpuCores,
                    containerNodeSpec2.minMainMemoryAvailableGb,
                    containerNodeSpec2.minDiskAvailableGb);

            // Wait until it is marked ready
            Optional<ContainerNodeSpec> tempContainerNodeSpec;
            while ((tempContainerNodeSpec = dockerTester.getContainerNodeSpec(containerNodeSpec2.hostname)).isPresent()
                    && tempContainerNodeSpec.get().nodeState != Node.State.ready) {
                Thread.sleep(10);
            }

            addAndWaitForNode(dockerTester, "host3", new ContainerName("container3"), Optional.of(new DockerImage("image1")));

            CallOrderVerifier callOrderVerifier = dockerTester.getCallOrderVerifier();
            callOrderVerifier.assertInOrder(
                    "createContainerCommand with DockerImage: DockerImage { imageId=image1 }, HostName: host1, ContainerName: ContainerName { name=container1 }",
                    "executeInContainer with ContainerName: ContainerName { name=container1 }, args: [/usr/bin/env, test, -x, " + DockerOperationsImpl.NODE_PROGRAM + "]",
                    "executeInContainer with ContainerName: ContainerName { name=container1 }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]",

                    "createContainerCommand with DockerImage: DockerImage { imageId=image2 }, HostName: host2, ContainerName: ContainerName { name=container2 }",
                    "executeInContainer with ContainerName: ContainerName { name=container2 }, args: [/usr/bin/env, test, -x, " + DockerOperationsImpl.NODE_PROGRAM + "]",
                    "executeInContainer with ContainerName: ContainerName { name=container2 }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]",

                    "stopContainer with ContainerName: ContainerName { name=container2 }",
                    "deleteContainer with ContainerName: ContainerName { name=container2 }",

                    "createContainerCommand with DockerImage: DockerImage { imageId=image1 }, HostName: host3, ContainerName: ContainerName { name=container3 }",
                    "executeInContainer with ContainerName: ContainerName { name=container3 }, args: [/usr/bin/env, test, -x, " + DockerOperationsImpl.NODE_PROGRAM + "]",
                    "executeInContainer with ContainerName: ContainerName { name=container3 }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]");

            callOrderVerifier.assertInOrderWithAssertMessage("Maintainer did not receive call to delete application storage",
                                                             "deleteContainer with ContainerName: ContainerName { name=container2 }",
                                                             "DeleteContainerStorage with ContainerName: ContainerName { name=container2 }");

            callOrderVerifier.assertInOrder(
                    "updateNodeAttributes with HostName: host1, NodeAttributes: NodeAttributes{restartGeneration=1, rebootGeneration=0, dockerImage=DockerImage { imageId=image1 }, vespaVersion=''}",
                    "updateNodeAttributes with HostName: host2, NodeAttributes: NodeAttributes{restartGeneration=1, rebootGeneration=0, dockerImage=DockerImage { imageId=image2 }, vespaVersion=''}",
                    "markAsReady with HostName: host2",
                    "updateNodeAttributes with HostName: host3, NodeAttributes: NodeAttributes{restartGeneration=1, rebootGeneration=0, dockerImage=DockerImage { imageId=image1 }, vespaVersion=''}");
        }
    }

    private ContainerNodeSpec addAndWaitForNode(DockerTester tester, String hostName, ContainerName containerName, Optional<DockerImage> dockerImage) throws InterruptedException {
        ContainerNodeSpec containerNodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .wantedDockerImage(dockerImage)
                .containerName(containerName)
                .nodeState(Node.State.active)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .wantedRestartGeneration(Optional.of(1L))
                .currentRestartGeneration(Optional.of(1L))
                .build();

        tester.addContainerNodeSpec(containerNodeSpec);

        // Wait for node admin to be notified with node repo state and the docker container has been started
        while (tester.getNodeAdmin().getListOfHosts().size() != tester.getNumberOfContainerSpecs()) {
            Thread.sleep(10);
        }

        tester.getCallOrderVerifier().assertInOrder(
                "createContainerCommand with DockerImage: " + dockerImage.get() + ", HostName: " + hostName + ", ContainerName: " + containerName,
                "executeInContainer with ContainerName: " + containerName + ", args: [/usr/bin/env, test, -x, " + DockerOperationsImpl.NODE_PROGRAM + "]",
                "executeInContainer with ContainerName: " + containerName + ", args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]");

        return containerNodeSpec;
    }
}

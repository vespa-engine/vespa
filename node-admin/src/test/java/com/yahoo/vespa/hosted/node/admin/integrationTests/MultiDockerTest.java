// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.NodeRepositoryNode;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

/**
 * @author freva
 */
public class MultiDockerTest {

    @Test
    public void test() throws InterruptedException {
        try (DockerTester dockerTester = new DockerTester()) {
            addAndWaitForNode(dockerTester, "host1.test.yahoo.com", new DockerImage("image1"));
            NodeRepositoryNode nodeRepositoryNode2 = addAndWaitForNode(
                    dockerTester, "host2.test.yahoo.com", new DockerImage("image2"));

            dockerTester.addContainerNodeSpec(
                    new NodeRepositoryNode.Builder(nodeRepositoryNode2)
                            .nodeState(Node.State.dirty)
                            .minCpuCores(1)
                            .minMainMemoryAvailableGb(1)
                            .minDiskAvailableGb(1)
                            .build());

            // Wait until it is marked ready
            while (dockerTester.nodeRepositoryMock.getContainerNodeSpec(nodeRepositoryNode2.hostname)
                    .filter(node -> node.nodeState != Node.State.ready).isPresent()) {
                Thread.sleep(10);
            }

            addAndWaitForNode(dockerTester, "host3.test.yahoo.com", new DockerImage("image1"));

            dockerTester.callOrderVerifier.assertInOrder(
                    "createContainerCommand with DockerImage { imageId=image1 }, HostName: host1.test.yahoo.com, ContainerName { name=host1 }",
                    "executeInContainerAsRoot with ContainerName { name=host1 }, args: [" + DockerTester.NODE_PROGRAM + ", resume]",

                    "createContainerCommand with DockerImage { imageId=image2 }, HostName: host2.test.yahoo.com, ContainerName { name=host2 }",
                    "executeInContainerAsRoot with ContainerName { name=host2 }, args: [" + DockerTester.NODE_PROGRAM + ", resume]",

                    "stopContainer with ContainerName { name=host2 }",
                    "deleteContainer with ContainerName { name=host2 }",

                    "createContainerCommand with DockerImage { imageId=image1 }, HostName: host3.test.yahoo.com, ContainerName { name=host3 }",
                    "executeInContainerAsRoot with ContainerName { name=host3 }, args: [" + DockerTester.NODE_PROGRAM + ", resume]");

            dockerTester.callOrderVerifier.assertInOrderWithAssertMessage(
                    "Maintainer did not receive call to delete application storage",
                    "deleteContainer with ContainerName { name=host2 }",
                     "DeleteContainerStorage with ContainerName { name=host2 }");

            dockerTester.callOrderVerifier.assertInOrder(
                    "updateNodeAttributes with HostName: host1.test.yahoo.com, NodeAttributes{restartGeneration=1, rebootGeneration=0, dockerImage=image1, vespaVersion='1.2.3', hardwareDivergence='null'}",
                    "updateNodeAttributes with HostName: host2.test.yahoo.com, NodeAttributes{restartGeneration=1, rebootGeneration=0, dockerImage=image2, vespaVersion='1.2.3', hardwareDivergence='null'}",
                    "markNodeAvailableForNewAllocation with HostName: host2.test.yahoo.com",
                    "updateNodeAttributes with HostName: host3.test.yahoo.com, NodeAttributes{restartGeneration=1, rebootGeneration=0, dockerImage=image1, vespaVersion='1.2.3', hardwareDivergence='null'}");
        }
    }

    private NodeRepositoryNode addAndWaitForNode(DockerTester tester, String hostName, DockerImage dockerImage) throws InterruptedException {
        NodeRepositoryNode nodeRepositoryNode = new NodeRepositoryNode.Builder()
                .hostname(hostName)
                .wantedDockerImage(dockerImage)
                .wantedVespaVersion("1.2.3")
                .nodeState(Node.State.active)
                .nodeType(NodeType.tenant)
                .nodeFlavor("docker")
                .wantedRestartGeneration(1L)
                .currentRestartGeneration(1L)
                .minCpuCores(1)
                .minMainMemoryAvailableGb(1)
                .minDiskAvailableGb(1)
                .build();

        tester.addContainerNodeSpec(nodeRepositoryNode);

        // Wait for node admin to be notified with node repo state and the docker container has been started
        while (tester.nodeAdmin.getListOfHosts().size() != tester.nodeRepositoryMock.getNumberOfContainerSpecs()) {
            Thread.sleep(10);
        }

        ContainerName containerName = ContainerName.fromHostname(hostName);
        tester.callOrderVerifier.assertInOrder(
                "createContainerCommand with " + dockerImage + ", HostName: " + hostName + ", " + containerName,
                "executeInContainerAsRoot with " + containerName + ", args: [" + DockerTester.NODE_PROGRAM + ", resume]");

        return nodeRepositoryNode;
    }
}

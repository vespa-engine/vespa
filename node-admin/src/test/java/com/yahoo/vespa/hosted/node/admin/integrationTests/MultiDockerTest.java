// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeState;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

/**
 * @author freva
 */
public class MultiDockerTest {

    @Test
    public void test() {
        try (DockerTester tester = new DockerTester()) {
            DockerImage image1 = DockerImage.fromString("registry.example.com/image1");
            addAndWaitForNode(tester, "host1.test.yahoo.com", image1);
            NodeSpec nodeSpec2 = addAndWaitForNode(
                    tester, "host2.test.yahoo.com", DockerImage.fromString("registry.example.com/image2"));

            tester.addChildNodeRepositoryNode(NodeSpec.Builder.testSpec(nodeSpec2.hostname(), NodeState.dirty).build());

            tester.inOrder(tester.containerEngine).deleteContainer(eq(new ContainerName("host2")));
            tester.inOrder(tester.storageMaintainer).archiveNodeStorage(
                    argThat(context -> context.containerName().equals(new ContainerName("host2"))));
            tester.inOrder(tester.nodeRepository).setNodeState(eq(nodeSpec2.hostname()), eq(NodeState.ready));

            addAndWaitForNode(tester, "host3.test.yahoo.com", image1);
        }
    }

    private NodeSpec addAndWaitForNode(DockerTester tester, String hostName, DockerImage dockerImage) {
        NodeSpec nodeSpec = NodeSpec.Builder.testSpec(hostName).wantedDockerImage(dockerImage).build();
        tester.addChildNodeRepositoryNode(nodeSpec);

        ContainerName containerName = ContainerName.fromHostname(hostName);
        tester.inOrder(tester.containerEngine).createContainerCommand(eq(dockerImage), eq(containerName));
        tester.inOrder(tester.containerEngine).executeInContainerAsUser(
                eq(containerName), eq("root"), any(), eq(DockerTester.NODE_PROGRAM), eq("resume"));
        tester.inOrder(tester.nodeRepository).updateNodeAttributes(eq(hostName),
                eq(new NodeAttributes().withDockerImage(dockerImage).withVespaVersion(dockerImage.tagAsVersion())));

        return nodeSpec;
    }
}

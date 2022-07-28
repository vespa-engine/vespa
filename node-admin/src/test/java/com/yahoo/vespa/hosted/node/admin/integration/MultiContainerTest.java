// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integration;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.container.ContainerName;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

/**
 * @author freva
 */
public class MultiContainerTest {

    @Test
    void test() {
        DockerImage image1 = DockerImage.fromString("registry.example.com/repo/image1");
        DockerImage image2 = DockerImage.fromString("registry.example.com/repo/image2");
        try (ContainerTester tester = new ContainerTester(List.of(image1, image2))) {
            addAndWaitForNode(tester, "host1.test.yahoo.com", image1);
            NodeSpec nodeSpec2 = addAndWaitForNode(tester, "host2.test.yahoo.com", image2);

            tester.addChildNodeRepositoryNode(NodeSpec.Builder.testSpec(nodeSpec2.hostname(), NodeState.dirty).build());

            ContainerName host2 = new ContainerName("host2");
            tester.inOrder(tester.containerOperations).removeContainer(containerMatcher(host2), any());
            tester.inOrder(tester.storageMaintainer).archiveNodeStorage(
                    argThat(context -> context.containerName().equals(host2)));
            tester.inOrder(tester.nodeRepository).setNodeState(eq(nodeSpec2.hostname()), eq(NodeState.ready));

            addAndWaitForNode(tester, "host3.test.yahoo.com", image1);
        }
    }

    private NodeAgentContext containerMatcher(ContainerName containerName) {
        return argThat((ctx) -> ctx.containerName().equals(containerName));
    }

    private NodeSpec addAndWaitForNode(ContainerTester tester, String hostName, DockerImage dockerImage) {
        NodeSpec nodeSpec = NodeSpec.Builder.testSpec(hostName).wantedDockerImage(dockerImage).build();
        tester.addChildNodeRepositoryNode(nodeSpec);

        ContainerName containerName = ContainerName.fromHostname(hostName);
        tester.inOrder(tester.containerOperations).createContainer(containerMatcher(containerName), any());
        tester.inOrder(tester.containerOperations).resumeNode(containerMatcher(containerName));
        tester.inOrder(tester.nodeRepository).updateNodeAttributes(eq(hostName), any());

        return nodeSpec;
    }

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integration;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.container.ContainerName;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.integration.ContainerTester.containerMatcher;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author freva
 */
public class ContainerFailTest {

    @Test
    void test() {
        DockerImage dockerImage = DockerImage.fromString("registry.example.com/repo/image");
        try (ContainerTester tester = new ContainerTester(List.of(dockerImage))) {
            ContainerName containerName = new ContainerName("host1");
            String hostname = "host1.test.yahoo.com";
            NodeSpec nodeSpec = NodeSpec.Builder
                    .testSpec(hostname)
                    .wantedDockerImage(dockerImage)
                    .currentDockerImage(dockerImage)
                    .build();
            tester.addChildNodeRepositoryNode(nodeSpec);

            NodeAgentContext context = NodeAgentContextImpl.builder(nodeSpec).fileSystem(TestFileSystem.create()).build();

            tester.inOrder(tester.containerOperations).createContainer(containerMatcher(containerName), any());
            tester.inOrder(tester.containerOperations).resumeNode(containerMatcher(containerName));

            tester.containerOperations.removeContainer(context, tester.containerOperations.getContainer(context).get());

            tester.inOrder(tester.containerOperations).removeContainer(containerMatcher(containerName), any());
            tester.inOrder(tester.containerOperations).createContainer(containerMatcher(containerName), any());
            tester.inOrder(tester.containerOperations).resumeNode(containerMatcher(containerName));

            verify(tester.nodeRepository, never()).updateNodeAttributes(any(), any());
        }
    }

}

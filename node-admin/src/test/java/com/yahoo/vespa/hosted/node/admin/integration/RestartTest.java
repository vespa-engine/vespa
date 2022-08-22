// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integration;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.container.ContainerName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.integration.ContainerTester.containerMatcher;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Tests that different wanted and current restart generation leads to execution of restart command
 *
 * @author musum
 */
public class RestartTest {

    @Test
    void test() {
        DockerImage dockerImage = DockerImage.fromString("registry.example.com/repo/image:1.2.3");
        try (ContainerTester tester = new ContainerTester(List.of(dockerImage))) {
            String hostname = "host1.test.yahoo.com";
            NodeSpec nodeSpec = NodeSpec.Builder.testSpec(hostname)
                                                .wantedDockerImage(dockerImage)
                                                .wantedVespaVersion(dockerImage.tagAsVersion())
                                                .build();
            tester.addChildNodeRepositoryNode(nodeSpec);

            ContainerName host1 = new ContainerName("host1");
            tester.inOrder(tester.containerOperations).createContainer(containerMatcher(host1), any());
            tester.inOrder(tester.nodeRepository).updateNodeAttributes(
                    eq(hostname), eq(new NodeAttributes().withDockerImage(dockerImage).withVespaVersion(dockerImage.tagAsVersion())));

            // Increment wantedRestartGeneration to 2 in node-repo
            tester.addChildNodeRepositoryNode(new NodeSpec.Builder(tester.nodeRepository.getNode(hostname))
                    .wantedRestartGeneration(2).build());

            tester.inOrder(tester.orchestrator).suspend(eq(hostname));
            tester.inOrder(tester.containerOperations).restartVespa(containerMatcher(host1));
            tester.inOrder(tester.nodeRepository).updateNodeAttributes(
                    eq(hostname), eq(new NodeAttributes().withRestartGeneration(2)));
        }
    }

}

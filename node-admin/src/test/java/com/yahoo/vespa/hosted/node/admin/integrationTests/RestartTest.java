// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import org.junit.Test;

import static com.yahoo.vespa.hosted.node.admin.integrationTests.DockerTester.NODE_PROGRAM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Tests that different wanted and current restart generation leads to execution of restart command
 *
 * @author musum
 */
public class RestartTest {

    @Test
    public void test() {
        try (DockerTester tester = new DockerTester()) {
            String hostname = "host1.test.yahoo.com";
            DockerImage dockerImage = DockerImage.fromString("registry.example.com/dockerImage:1.2.3");

            tester.addChildNodeRepositoryNode(NodeSpec.Builder.testSpec(hostname).wantedDockerImage(dockerImage).build());

            tester.inOrder(tester.containerEngine).createContainerCommand(eq(dockerImage), eq(new ContainerName("host1")));
            tester.inOrder(tester.nodeRepository).updateNodeAttributes(
                    eq(hostname), eq(new NodeAttributes().withDockerImage(dockerImage).withVespaVersion(dockerImage.tagAsVersion())));

            // Increment wantedRestartGeneration to 2 in node-repo
            tester.addChildNodeRepositoryNode(new NodeSpec.Builder(tester.nodeRepository.getNode(hostname))
                    .wantedRestartGeneration(2).build());

            tester.inOrder(tester.orchestrator).suspend(eq(hostname));
            tester.inOrder(tester.containerEngine).executeInContainerAsUser(
                    eq(new ContainerName("host1")), any(), any(), eq(NODE_PROGRAM), eq("restart-vespa"));
            tester.inOrder(tester.nodeRepository).updateNodeAttributes(
                    eq(hostname), eq(new NodeAttributes().withRestartGeneration(2)));
        }
    }
}

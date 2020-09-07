// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.config.provision.ContainerImage;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author freva
 */
public class DockerFailTest {

    @Test
    public void dockerFailTest() {
        try (DockerTester tester = new DockerTester()) {
            final ContainerImage containerImage = ContainerImage.fromString("containerImage");
            final ContainerName containerName = new ContainerName("host1");
            final String hostname = "host1.test.yahoo.com";
            tester.addChildNodeRepositoryNode(NodeSpec.Builder
                    .testSpec(hostname)
                    .wantedDockerImage(containerImage)
                    .currentDockerImage(containerImage)
                    .build());

            tester.inOrder(tester.containerEngine).createContainerCommand(eq(containerImage), eq(containerName));
            tester.inOrder(tester.containerEngine).executeInContainerAsUser(
                    eq(containerName), eq("root"), any(), eq(DockerTester.NODE_PROGRAM), eq("resume"));

            tester.containerEngine.deleteContainer(new ContainerName("host1"));

            tester.inOrder(tester.containerEngine).deleteContainer(eq(containerName));
            tester.inOrder(tester.containerEngine).createContainerCommand(eq(containerImage), eq(containerName));
            tester.inOrder(tester.containerEngine).executeInContainerAsUser(
                    eq(containerName), eq("root"), any(), eq(DockerTester.NODE_PROGRAM), eq("resume"));

            verify(tester.nodeRepository, never()).updateNodeAttributes(any(), any());
        }
    }
}

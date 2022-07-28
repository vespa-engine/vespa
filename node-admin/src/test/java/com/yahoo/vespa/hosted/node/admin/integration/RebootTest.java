// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integration;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.container.ContainerName;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.integration.ContainerTester.HOST_HOSTNAME;
import static com.yahoo.vespa.hosted.node.admin.integration.ContainerTester.containerMatcher;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Tests rebooting of Docker host
 *
 * @author musum
 */
public class RebootTest {

    private final String hostname = "host1.test.yahoo.com";
    private final DockerImage dockerImage = DockerImage.fromString("registry.example.com/repo/image");

    @Test
    void test() {
        try (ContainerTester tester = new ContainerTester(List.of(dockerImage))) {
            tester.addChildNodeRepositoryNode(NodeSpec.Builder.testSpec(hostname).wantedDockerImage(dockerImage).build());

            ContainerName host1 = new ContainerName("host1");
            tester.inOrder(tester.containerOperations).createContainer(containerMatcher(host1), any());

            tester.setWantedState(NodeAdminStateUpdater.State.SUSPENDED);

            tester.inOrder(tester.orchestrator).suspend(eq(HOST_HOSTNAME.value()), eq(List.of(hostname, HOST_HOSTNAME.value())));
            tester.inOrder(tester.containerOperations).stopServices(containerMatcher(host1));
            assertTrue(tester.nodeAdmin.setFrozen(true));
        }
    }

}

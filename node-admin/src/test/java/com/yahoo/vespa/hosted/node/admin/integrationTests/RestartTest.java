// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.net.UnknownHostException;

/**
 * Tests that different wanted and current restart generation leads to execution of restart command
 *
 * @author musum
 */
public class RestartTest {

    @Test
    public void test() throws InterruptedException, UnknownHostException {
        try (DockerTester dockerTester = new DockerTester()) {

            long wantedRestartGeneration = 1;
            long currentRestartGeneration = wantedRestartGeneration;
            dockerTester.addContainerNodeSpec(createContainerNodeSpec(wantedRestartGeneration, currentRestartGeneration));

            // Wait for node admin to be notified with node repo state and the docker container has been started
            while (dockerTester.nodeAdmin.getListOfHosts().size() == 0) {
                Thread.sleep(10);
            }

            // Check that the container is started and NodeRepo has received the PATCH update
            dockerTester.callOrderVerifier.assertInOrder(
                    "createContainerCommand with DockerImage { imageId=image:1.2.3 }, HostName: host1.test.yahoo.com, ContainerName { name=host1 }",
                    "updateNodeAttributes with HostName: host1.test.yahoo.com, NodeAttributes{restartGeneration=1, rebootGeneration=0, dockerImage=image:1.2.3, vespaVersion='1.2.3', hardwareDivergence='null'}");

            wantedRestartGeneration = 2;
            currentRestartGeneration = 1;
            dockerTester.addContainerNodeSpec(createContainerNodeSpec(wantedRestartGeneration, currentRestartGeneration));

            dockerTester.callOrderVerifier.assertInOrder(
                    "Suspend for host1.test.yahoo.com",
                    "executeInContainerAsRoot with ContainerName { name=host1 }, args: [" + DockerTester.NODE_PROGRAM + ", restart-vespa]");
        }
    }

    private ContainerNodeSpec createContainerNodeSpec(long wantedRestartGeneration, long currentRestartGeneration) {
        return new ContainerNodeSpec.Builder()
                .hostname("host1.test.yahoo.com")
                .nodeState(Node.State.active)
                .wantedDockerImage(new DockerImage("image:1.2.3"))
                .wantedVespaVersion("1.2.3")
                .nodeType(NodeType.tenant)
                .nodeFlavor("docker")
                .wantedRestartGeneration(wantedRestartGeneration)
                .currentRestartGeneration(currentRestartGeneration)
                .minCpuCores(1)
                .minMainMemoryAvailableGb(1)
                .minDiskAvailableGb(1)
                .build();
    }
}

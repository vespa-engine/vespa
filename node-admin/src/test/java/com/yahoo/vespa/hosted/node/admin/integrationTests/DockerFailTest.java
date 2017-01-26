// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

/**
 * @author freva
 */
public class DockerFailTest {

    @Test
    public void dockerFailTest() throws Exception {
        try (DockerTester dockerTester = new DockerTester()) {
            ContainerNodeSpec containerNodeSpec = new ContainerNodeSpec.Builder()
                    .hostname("hostName")
                    .wantedDockerImage(new DockerImage("dockerImage"))
                    .containerName(new ContainerName("container"))
                    .nodeState(Node.State.active)
                    .nodeType("tenant")
                    .nodeFlavor("docker")
                    .wantedRestartGeneration(1L)
                    .currentRestartGeneration(1L)
                    .build();
            dockerTester.addContainerNodeSpec(containerNodeSpec);

            // Wait for node admin to be notified with node repo state and the docker container has been started
            while (dockerTester.getNodeAdmin().getListOfHosts().size() == 0) {
                Thread.sleep(10);
            }

            CallOrderVerifier callOrderVerifier = dockerTester.getCallOrderVerifier();
            callOrderVerifier.assertInOrder(
                    "createContainerCommand with DockerImage { imageId=dockerImage }, HostName: hostName, ContainerName { name=container }",
                    "executeInContainerAsRoot with ContainerName { name=container }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]");

            dockerTester.deleteContainer(containerNodeSpec.containerName);

            callOrderVerifier.assertInOrder(
                    "deleteContainer with ContainerName { name=container }",
                    "createContainerCommand with DockerImage { imageId=dockerImage }, HostName: hostName, ContainerName { name=container }",
                    "executeInContainerAsRoot with ContainerName { name=container }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]");
        }
    }
}

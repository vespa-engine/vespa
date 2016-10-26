// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.util.Optional;

/**
 * @author valerijf
 */
public class DockerFailTest {

    @Test
    public void dockerFailTest() throws Exception {
        try (DockerTester dockerTester = new DockerTester()) {
            final ContainerNodeSpec containerNodeSpec = new ContainerNodeSpec(
                    "hostName",
                    Optional.of(new DockerImage("dockerImage")),
                    new ContainerName("container"),
                    Node.State.active,
                    "tenant",
                    "docker",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(1L),
                    Optional.of(1L),
                    Optional.of(1d),
                    Optional.of(1d),
                    Optional.of(1d));
            dockerTester.addContainerNodeSpec(containerNodeSpec);

            // Wait for node admin to be notified with node repo state and the docker container has been started
            while (dockerTester.getNodeAdmin().getListOfHosts().size() == 0) {
                Thread.sleep(10);
            }

            CallOrderVerifier callOrderVerifier = dockerTester.getCallOrderVerifier();
            callOrderVerifier.assertInOrder(
                    "createContainerCommand with DockerImage: DockerImage { imageId=dockerImage }, HostName: hostName, ContainerName: ContainerName { name=container }",
                    "executeInContainer with ContainerName: ContainerName { name=container }, args: [/usr/bin/env, test, -x, " + DockerOperationsImpl.NODE_PROGRAM + "]",
                    "executeInContainer with ContainerName: ContainerName { name=container }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]");

            dockerTester.deleteContainer(containerNodeSpec.containerName);

            callOrderVerifier.assertInOrder(
                    "deleteContainer with ContainerName: ContainerName { name=container }",
                    "createContainerCommand with DockerImage: DockerImage { imageId=dockerImage }, HostName: hostName, ContainerName: ContainerName { name=container }",
                    "executeInContainer with ContainerName: ContainerName { name=container }, args: [/usr/bin/env, test, -x, " + DockerOperationsImpl.NODE_PROGRAM + "]",
                    "executeInContainer with ContainerName: ContainerName { name=container }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]");
        }
    }
}

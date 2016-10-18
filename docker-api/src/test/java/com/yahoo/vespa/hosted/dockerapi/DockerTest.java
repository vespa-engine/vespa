// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * Class for testing full integration with docker daemon, requires running daemon. To run these tests:
 *
 * MAC:
 *   1. Install Docker Toolbox, and start it (Docker Quickstart Terminal) (you can close terminal window afterwards)
 *   2. For network test, we need to make docker containers visible for Mac: sudo route add 172.0.0.0/8 192.168.99.100
 *   2. Run tests from IDE/mvn.
 *
 *
 * TIPS:
 *   For cleaning up your local docker machine (DON'T DO THIS ON PROD)
 *     docker stop $(docker ps -a -q)
 *     docker rm $(docker ps -a -q)
 *
 * @author valerijf
 * @author dybdahl
 */
public class DockerTest {
    private DockerImpl docker;
    private static final DockerImage dockerImage = new DockerImage("simple-ipv6-server:Dockerfile");

    // It is ignored since it is a bit slow and unstable, at least on Mac.
    @Ignore
    @Test
    public void testDockerImagePull() throws ExecutionException, InterruptedException {
        DockerImage dockerImage = new DockerImage("busybox:1.24.0");

        // Pull the image and wait for the pull to complete
        docker.pullImageAsync(dockerImage).get();

        List<DockerImage> unusedDockerImages = docker.getUnusedDockerImages(new HashSet<>());
        if (! unusedDockerImages.contains(dockerImage)) {
            fail("Did not find image as unused, here are all the unused images; " + unusedDockerImages);
        }
        // Remove the image
        docker.deleteImage(dockerImage);
        assertFalse("Failed to delete " + dockerImage.asString() + " image", docker.imageIsDownloaded(dockerImage));
    }

    public void testContainerCycle() throws IOException, InterruptedException, ExecutionException {
        ContainerName containerName = new ContainerName("foo");
        docker.createContainerCommand(dockerImage, containerName, "hostName1").create();
        List<Container> managedContainers = docker.getAllManagedContainers();
        assertThat(managedContainers.size(), is(1));
        assertThat(managedContainers.get(0).name, is(containerName));
        assertThat(managedContainers.get(0).isRunning, is(false));

        docker.startContainer(containerName);
        managedContainers = docker.getAllManagedContainers();
        assertThat(managedContainers.size(), is(1));
        assertThat(managedContainers.get(0).name, is(containerName));
        assertThat(managedContainers.get(0).isRunning, is(true));

        docker.stopContainer(containerName);
        managedContainers = docker.getAllManagedContainers();
        assertThat(managedContainers.size(), is(1));
        assertThat(managedContainers.get(0).name, is(containerName));
        assertThat(managedContainers.get(0).isRunning, is(false));

        docker.deleteContainer(containerName);
        assertThat(docker.getAllManagedContainers().isEmpty(), is(true));
    }

    public void testDockerNetworking() throws InterruptedException, ExecutionException, IOException {
        String hostName1 = "docker10.test.yahoo.com";
        String hostName2 = "docker11.test.yahoo.com";
        ContainerName containerName1 = new ContainerName("test-container-1");
        ContainerName containerName2 = new ContainerName("test-container-2");
        InetAddress inetAddress1 = InetAddress.getByName("172.18.0.10");
        InetAddress inetAddress2 = InetAddress.getByName("172.18.0.11");

        docker.createContainerCommand(dockerImage, containerName1, hostName1)
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).withIpAddress(inetAddress1).create();
        docker.startContainer(containerName1);

        docker.createContainerCommand(dockerImage, containerName2, hostName2)
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).withIpAddress(inetAddress2).create();
        docker.startContainer(containerName2);

        testReachabilityFromHost("http://" + inetAddress1.getHostAddress() + "/ping");
        testReachabilityFromHost("http://" + inetAddress2.getHostAddress() + "/ping");

        String[] curlFromNodeToNode = new String[]{"curl", "-g", "http://" + inetAddress2.getHostAddress() + "/ping"};
        ProcessResult result = docker.executeInContainer(containerName1, curlFromNodeToNode);
        assertThat("Could not reach " + containerName2.asString() + " from " + containerName1.asString(),
                result.getOutput(), is("pong\n"));

        docker.stopContainer(containerName1);
        docker.deleteContainer(containerName1);

        docker.stopContainer(containerName2);
        docker.deleteContainer(containerName2);
    }

    @Before
    public void setup() throws InterruptedException, ExecutionException, IOException {
        if (docker == null) {
            assumeTrue(DockerTestUtils.dockerDaemonIsPresent());

            docker = DockerTestUtils.getDocker();
            DockerTestUtils.createDockerTestNetworkIfNeeded(docker);
            DockerTestUtils.createDockerImage(docker, dockerImage);
        }

        // Clean up any non deleted containers from previous tests
        docker.getAllManagedContainers().forEach(container -> {
            if (container.isRunning) docker.stopContainer(container.name);
            docker.deleteContainer(container.name);
        });
    }

    private void testReachabilityFromHost(String target) throws IOException, InterruptedException {
        URL url = new URL(target);
        String containerServer = IOUtils.toString(url.openStream());
        assertThat(containerServer, is("pong\n"));
    }
}

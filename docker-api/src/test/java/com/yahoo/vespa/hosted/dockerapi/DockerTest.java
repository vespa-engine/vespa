// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Requires docker daemon, see {@link com.yahoo.vespa.hosted.dockerapi.DockerTestUtils} for more details.
 *
 * @author freva
 * @author dybdahl
 */
public class DockerTest {
    private DockerImpl docker;
    private static final DockerImage dockerImage = new DockerImage("simple-ipv6-server:Dockerfile");
    private static final String MANAGER_NAME = "docker-test";

    // It is ignored since it is a bit slow and unstable, at least on Mac.
    @Ignore
    @Test
    public void testDockerImagePullDelete() throws ExecutionException, InterruptedException {
        DockerImage dockerImage = new DockerImage("busybox:1.24.0");

        // Pull the image and wait for the pull to complete
        docker.pullImageAsync(dockerImage).get();
        assertTrue("Failed to download " + dockerImage.asString() + " image", docker.imageIsDownloaded(dockerImage));

        // Remove the image
        docker.deleteImage(dockerImage);
        assertFalse("Failed to delete " + dockerImage.asString() + " image", docker.imageIsDownloaded(dockerImage));
    }

    // Ignored because the test is very slow (several minutes) when swap is enabled, to disable: (Linux)
    // $ sudo swapoff -a
    @Ignore
    @Test
    public void testOutOfMemoryDoesNotAffectOtherContainers() throws InterruptedException, ExecutionException, IOException {
        String hostName1 = "docker10.test.yahoo.com";
        String hostName2 = "docker11.test.yahoo.com";
        ContainerName containerName1 = new ContainerName("docker-test-1");
        ContainerName containerName2 = new ContainerName("docker-test-2");
        InetAddress inetAddress1 = InetAddress.getByName("172.18.10.10");
        InetAddress inetAddress2 = InetAddress.getByName("172.18.10.11");

        docker.createContainerCommand(dockerImage, containerName1, hostName1)
                .withManagedBy(MANAGER_NAME)
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                .withIpAddress(inetAddress1)
                .withMemoryInMb(100).create();
        docker.startContainer(containerName1);

        docker.createContainerCommand(dockerImage, containerName2, hostName2)
                .withManagedBy(MANAGER_NAME)
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                .withIpAddress(inetAddress2)
                .withMemoryInMb(100).create();
        docker.startContainer(containerName2);

        // 137 = 128 + 9 = kill -9 (SIGKILL), doesn't need to be run as "root", but "yahoo" does not exist in this basic image
        assertThat(docker.executeInContainerAsRoot(containerName2, "python", "/pysrc/fillmem.py", "90").getExitStatus(), is(137));

        // Verify that both HTTP servers are still up
        testReachabilityFromHost("http://" + inetAddress1.getHostAddress() + "/ping");
        testReachabilityFromHost("http://" + inetAddress2.getHostAddress() + "/ping");

        docker.stopContainer(containerName1);
        docker.deleteContainer(containerName1);

        docker.stopContainer(containerName2);
        docker.deleteContainer(containerName2);
    }

    @Test
    public void testContainerCycle() throws IOException, InterruptedException, ExecutionException {
        final ContainerName containerName = new ContainerName("docker-test-foo");
        final String containerHostname = "hostName1";

        docker.createContainerCommand(dockerImage, containerName, containerHostname).withManagedBy(MANAGER_NAME).create();
        Optional<Container> container = docker.getContainer(containerName);
        assertTrue(container.isPresent());
        assertEquals(container.get().state, Container.State.CREATED);

        docker.startContainer(containerName);
        container = docker.getContainer(containerName);
        assertTrue(container.isPresent());
        assertEquals(container.get().state, Container.State.RUNNING);

        docker.dockerClient.pauseContainerCmd(containerName.asString()).exec();
        container = docker.getContainer(containerName);
        assertTrue(container.isPresent());
        assertEquals(container.get().state, Container.State.PAUSED);

        docker.dockerClient.unpauseContainerCmd(containerName.asString()).exec();
        docker.stopContainer(containerName);
        container = docker.getContainer(containerName);
        assertTrue(container.isPresent());
        assertEquals(container.get().state, Container.State.EXITED);

        docker.deleteContainer(containerName);
        assertThat(docker.getAllContainersManagedBy(MANAGER_NAME).isEmpty(), is(true));
    }

    /**
     * Test the expected behavior for exec when it times out - it should throw an exception when it times out,
     * and before the process completes.
     *
     * The test timeout value is set quite high to avoid noise if screwdriver is slow but lower than the process time.
     */
    @Test(expected = DockerExecTimeoutException.class, timeout = 2000)
    public void testContainerExecHounorsTimeout() throws IOException, InterruptedException, ExecutionException {
        final ContainerName containerName = new ContainerName("docker-test-foo");
        final String containerHostname = "hostName1";

        docker.createContainerCommand(dockerImage, containerName, containerHostname).withManagedBy(MANAGER_NAME).create();
        docker.startContainer(containerName);
        docker.executeInContainerAsRoot(containerName, 1L, "sh", "-c", "sleep 5");
    }

    /**
     * Test the expected behavior for exec that completes before specified timeout - it should return when the process finishes and not
     * wait for the timeout. Some previous tests indicated that this was not behaving correctly.
     *
     * No timeout implies infinite timeout.
     *
     * The test timeout value is set quite high to avoid noise if screwdriver is slow
     */
    @Test(timeout = 4000)
    public void testContainerExecDoesNotBlockUntilTimeoutWhenCommandFinishesBeforeTimeout() throws IOException, InterruptedException, ExecutionException {
        final ContainerName containerName = new ContainerName("docker-test-foo");
        final String containerHostname = "hostName1";

        docker.createContainerCommand(dockerImage, containerName, containerHostname).withManagedBy(MANAGER_NAME).create();
        docker.startContainer(containerName);
        docker.executeInContainerAsRoot(containerName, 2L, "sh", "-c", "echo hei");

        // Also test that this is the behavoir when not specifying timeout
        docker.executeInContainerAsRoot(containerName,"sh", "-c", "echo hei");
    }

    @Test
    public void testDockerNetworking() throws InterruptedException, ExecutionException, IOException {
        String hostName1 = "docker10.test.yahoo.com";
        String hostName2 = "docker11.test.yahoo.com";
        ContainerName containerName1 = new ContainerName("docker-test-1");
        ContainerName containerName2 = new ContainerName("docker-test-2");
        InetAddress inetAddress1 = InetAddress.getByName("172.18.10.10");
        InetAddress inetAddress2 = InetAddress.getByName("172.18.10.11");

        docker.createContainerCommand(dockerImage, containerName1, hostName1).withManagedBy(MANAGER_NAME)
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).withIpAddress(inetAddress1).create();
        docker.startContainer(containerName1);

        docker.createContainerCommand(dockerImage, containerName2, hostName2).withManagedBy(MANAGER_NAME)
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).withIpAddress(inetAddress2).create();
        docker.startContainer(containerName2);

        testReachabilityFromHost("http://" + inetAddress1.getHostAddress() + "/ping");
        testReachabilityFromHost("http://" + inetAddress2.getHostAddress() + "/ping");

        String[] curlFromNodeToNode = new String[]{"curl", "-g", "http://" + inetAddress2.getHostAddress() + "/ping"};
        ProcessResult result = docker.executeInContainerAsRoot(containerName1, curlFromNodeToNode);
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
            DockerTestUtils.buildSimpleHttpServerDockerImage(docker, dockerImage);
        }

        // Clean up any non deleted containers from previous tests
        docker.getAllContainersManagedBy(MANAGER_NAME).forEach(container -> {
            if (container.state.isRunning()) docker.stopContainer(container.name);
            docker.deleteContainer(container.name);
        });
    }

    private void testReachabilityFromHost(String target) throws IOException, InterruptedException {
        URL url = new URL(target);
        String containerServer = IOUtils.toString(url.openStream());
        assertThat(containerServer, is("pong\n"));
    }
}

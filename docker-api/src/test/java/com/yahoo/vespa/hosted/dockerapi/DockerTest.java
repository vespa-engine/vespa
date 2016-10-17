// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Class for testing full integration with docker daemon, requires running daemon. To run these tests:
 *
 * MAC:
 *   1. Install Docker Toolbox, and start it (Docker Quick Start Terminal) (you can close terminal window afterwards)
 *   2. Run tests from IDE/mvn.
 *
 * @author valerijf
 * @author dybdahl
 */
public class DockerTest {
    private DockerImpl docker;
    private static final OS operatingSystem = getSystemOS();
    private static final String prefix = "/Users/" + System.getProperty("user.name") + "/.docker/machine/machines/default/";
    private static final DockerConfig dockerConfig = new DockerConfig(new DockerConfig.Builder()
            .caCertPath(operatingSystem == OS.Mac_OS_X ? prefix + "ca.pem" : "")
            .clientCertPath(operatingSystem == OS.Mac_OS_X ? prefix + "cert.pem" : "")
            .clientKeyPath(operatingSystem == OS.Mac_OS_X ? prefix + "key.pem" : "")
            .uri(operatingSystem == OS.Mac_OS_X ? "tcp://192.168.99.100:2376" : "tcp://localhost:2376"));
    private static final DockerImage dockerImage = new DockerImage("simple-ipv6-server:Dockerfile");

    @Test
    public void testGetAllManagedContainersNoContainersRunning() {
        assumeTrue(operatingSystem != OS.Unsupported);
        assumeTrue(dockerDaemonIsPresent());

        List<Container> containers = docker.getAllManagedContainers();
        assertThat(containers.isEmpty(), is(true));
    }

    @Test
    public void testDockerImagePull() throws ExecutionException, InterruptedException {
        assumeTrue(dockerDaemonIsPresent());
        DockerImpl docker = new DockerImpl(dockerConfig);

        docker.getAllManagedContainers();
        DockerImage dockerImage = new DockerImage("busybox:1.24.0");

        // Pull the image and wait for the pull to complete
        docker.pullImageAsync(dockerImage).get();

        // Translate the human readable ID to sha256-hash ID that is returned by getUnusedDockerImages()
        DockerImage targetImage = new DockerImage(docker.dockerClient.inspectImageCmd(dockerImage.asString()).exec().getId());
        List<DockerImage> unusedDockerImages = docker.getUnusedDockerImages(new HashSet<>());
        if (! unusedDockerImages.contains(dockerImage)) {
            fail("Did not find image as unused, here are all images; " + unusedDockerImages);
        }
        // Remove the image
        docker.deleteImage(dockerImage);
        assertFalse("Failed to delete " + dockerImage.asString() + " image", docker.imageIsDownloaded(dockerImage));
    }

    @Test
    public void testCreateDeleteImageCreateDeleteContainer() throws IOException, InterruptedException, ExecutionException {
        assumeTrue(dockerDaemonIsPresent());
        createDockerImage(docker);

        ContainerName containerName = new ContainerName("foo");
        docker.stopContainer(containerName);
        docker.deleteContainer(containerName);
        assertThat(docker.getAllManagedContainers().isEmpty(), is(true));
        docker.createContainerCommand(dockerImage, containerName, "hostName1").create();
        List<Container> containers = docker.getAllManagedContainers();
        assertThat(containers.size(), is(1));
        docker.deleteContainer(containerName);

        docker.pullImageAsync(dockerImage).get();

        // Translate the human readable ID to sha256-hash ID that is returned by getUnusedDockerImages()
        DockerImage targetImage = new DockerImage(docker.dockerClient.inspectImageCmd(dockerImage.asString()).exec().getId());
        Set<DockerImage> except = new HashSet<>();
        List<DockerImage> x = docker.getUnusedDockerImages(except);

        // Remove the image
        docker.deleteImage(dockerImage);
        List<DockerImage> y = docker.getUnusedDockerImages(except);

        assertFalse("Failed to delete " + dockerImage.asString() + " image", docker.imageIsDownloaded(dockerImage));
    }

    @Test
    public void testDockerNetworking() throws InterruptedException, ExecutionException, IOException {
        assumeTrue(dockerDaemonIsPresent());
        createDockerImage(docker);

        String hostName1 = "docker10.test.yahoo.com";
        String hostName2 = "docker11.test.yahoo.com";
        ContainerName containerName1 = new ContainerName("test-container-1");
        ContainerName containerName2 = new ContainerName("test-container-2");
        InetAddress inetAddress1 = InetAddress.getByName("172.18.0.10");
        InetAddress inetAddress2 = InetAddress.getByName("172.18.0.11");

        docker.createContainerCommand(dockerImage, containerName1, hostName1)
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).withIpAddress(inetAddress1).create();

        docker.createContainerCommand(dockerImage, containerName2, hostName2)
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).withIpAddress(inetAddress2).create();

        docker.startContainer(containerName1);
        docker.startContainer(containerName2);

        try {
            testReachabilityFromHost(containerName1, inetAddress1);
            testReachabilityFromHost(containerName2, inetAddress2);

            String[] curlFromNodeToNode = new String[]{"curl", "-g", "http://" + inetAddress2 + "/ping"};
            while (! docker.executeInContainer(containerName1, curlFromNodeToNode).isSuccess()) {
                Thread.sleep(20);
            }
            ProcessResult result = docker.executeInContainer(containerName1, curlFromNodeToNode);
            assertTrue("Could not reach " + containerName2.asString() + " from " + containerName1.asString(),
                    result.getOutput().equals("pong\n"));
        } finally {
            docker.stopContainer(containerName1);
            docker.deleteContainer(containerName1);

            docker.stopContainer(containerName2);
            docker.deleteContainer(containerName2);
        }
    }

    private void createDockerTestNetworkIfNeeded() {
        if (! docker.dockerClient.listNetworksCmd().withNameFilter(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).exec().isEmpty()) return;

        Network.Ipam ipam = new Network.Ipam().withConfig(new Network.Ipam.Config().withSubnet("172.18.0.0/16"));
        docker.dockerClient.createNetworkCmd()
                .withName(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).withDriver("bridge").withIpam(ipam).exec();
    }

    private boolean dockerDaemonIsPresent() {
        if (docker != null) return true;
        if (operatingSystem == OS.Unsupported) {
            System.out.println("This test does not support " + System.getProperty("os.name") + " yet, ignoring test.");
            return false;
        }

        try {
            docker = new DockerImpl(dockerConfig);
            createDockerTestNetworkIfNeeded();
            return true;
        } catch (Exception e) {
            System.out.println("Please install Docker Toolbox and start Docker Quick Start Terminal once, ignoring test.");
            e.printStackTrace();
            return false;
        }
    }

    private void testReachabilityFromHost(ContainerName containerName, InetAddress target) throws IOException, InterruptedException {
        String[] curlNodeFromHost = {"curl", "-g", "http://" + target.getHostAddress() + "/ping"};
        while (!exec(curlNodeFromHost).equals("pong\n")) {
            Thread.sleep(20);
        }
        assertTrue("Could not reach " + containerName.asString() + " from host", exec(curlNodeFromHost).equals("pong\n"));
    }

    /**
     * Synchronously executes a system process and returns its stdout. Based of {@link com.yahoo.system.ProcessExecuter}
     * but could not be reused because of import errors.
     */
    private static String exec(String[] command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        StringBuilder ret = new StringBuilder();

        Process p = pb.start();
        InputStream is = p.getInputStream();
        while (true) {
            int b = is.read();
            if (b==-1) break;
            ret.append((char) b);
        }

        p.waitFor();
        p.destroy();

        return ret.toString();
    }

    private void createDockerImage(DockerImpl docker) throws IOException, ExecutionException, InterruptedException {
        try {
            docker.deleteImage(new DockerImage(dockerImage.asString()));
        } catch (Exception e) {
            assertThat(e.getMessage(), is("Failed to delete docker image " + dockerImage.asString()));
        }

        // Build the image locally
        File dockerFilePath = new File("src/test/resources/simple-ipv6-server");
        docker.dockerClient
                .buildImageCmd(dockerFilePath)
                .withTag(dockerImage.asString()).exec(new BuildImageResultCallback()).awaitCompletion();
    }

    private enum OS {Linux, Mac_OS_X, Unsupported}

    private static OS getSystemOS() {
        switch (System.getProperty("os.name").toLowerCase()) {
            case "linux": return OS.Linux;
            case "mac os x": return OS.Mac_OS_X;
            default: return OS.Unsupported;
        }
    }
}

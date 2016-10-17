// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
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
 *   1. Install Docker Toolbox, and start it (Docker Quickstart Terminal) (you can close terminal window afterwards)
 *   2. For network test, we need to make docker containers visible for Mac: sudo route add 172.0.0.0/8 192.168.99.100
 *   2. Run tests from IDE/mvn.
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
    public static final String SUBNET_CONTAINER = "172.18.7.2/24";
    public static final String IP_ADDRESS_FOR_CONTAINER = "172.18.7.32";

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
        assumeTrue(dockerDaemonIsPresent());

        List<Container> containers = docker.getAllManagedContainers();
        assertThat(containers.isEmpty(), is(true));
    }

    // It is ignored since it is a bit slow and unstable, at least on Mac.
    @Ignore
    @Test
    public void testDockerImagePull() throws ExecutionException, InterruptedException {
        assumeTrue(dockerDaemonIsPresent());

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
    public void testContainerCycle() throws IOException, InterruptedException, ExecutionException {
        assumeTrue(dockerDaemonIsPresent());

        DockerImage busyBoxImage = new DockerImage("busybox:1.24.0");
        // Pull the image and wait for the pull to complete
        System.out.print("Pulling image.");
        docker.pullImageAsync(busyBoxImage).get();
        System.out.println("... done");

        ContainerName containerName = new ContainerName("DummyWebServer");

        try { // Stop any old container
            docker.stopContainer(containerName);
            System.out.println("Stopped an old container with same name");
        } catch (Exception e) {
            System.out.println("Did not manage to stop container " + e.getMessage());
        }
        try { // Delete any remaining container
            docker.deleteContainer(containerName);
            System.out.println("Deleted an old container with same name");
        } catch (Exception e) {
            System.out.println("Did not manage to delete container " + e.getMessage());
        }

        int webPortForContainer = 6342;

        InetAddress inetAddress1 = Inet6Address.getByName(IP_ADDRESS_FOR_CONTAINER);
        docker.createContainerCommand(busyBoxImage, containerName, "hostName1")
                .withNetworkMode("vespa-macvlan")
                .withIpAddress(inetAddress1)
                .withCmd("sleep").withCmd("55555").create();

        System.out.print("Starting container with sleep running.");
        docker.startContainer(containerName);
        System.out.println("..done");


        // TODO: Get the container to start with httpd instead of hacking it here.
        new Thread(() -> {
            try {
                // This call is blocking
                docker.executeInContainer(containerName, "/bin/sh", "-c", "/bin/httpd -f -p " + webPortForContainer).getOutput();
            } catch (RuntimeException e) {
                // ignore, fails on shut down
            }
        }).start();

        boolean success = false;
        for (int x = 0; x < 600; x++) {
            try {
                URL url = new URL("http://" + IP_ADDRESS_FOR_CONTAINER + ":" + webPortForContainer);
                InputStream is = url.openStream();
                String containerServer = IOUtils.toString(is);
                assertThat(containerServer, is("sdf"));
            } catch (FileNotFoundException e) {
                System.out.println("Managed to talk to web server in container.");
                success = true;
                break;
            } catch (Throwable t) {
                System.err.println(t.getMessage());
                Thread.sleep(100);
            }
        }
        docker.stopContainer(containerName);
        docker.deleteContainer(containerName);
        // Do not bother, it takes forever to load it again.. docker.deleteImage(busyBoxImage);
        assertThat(success, is(true));
    }

    @Test
    public void testDockerNetworking() throws InterruptedException, ExecutionException, IOException {
        assumeTrue(dockerDaemonIsPresent());

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
        assumeTrue(dockerDaemonIsPresent());

        // Clean up any non deleted containers from previous tests
        docker.getAllManagedContainers().forEach(container -> {
            if (container.isRunning) docker.stopContainer(container.name);
            docker.deleteContainer(container.name);
        });
    }

    private boolean dockerDaemonIsPresent() {
        if (docker != null) return true;
        if (operatingSystem == OS.Unsupported) {
            System.out.println("This test does not support " + System.getProperty("os.name") + " yet, ignoring test.");
            return false;
        }

        try {
            setDocker();
            createDockerTestNetworkIfNeeded();
            createDockerImage();
            return true;
        } catch (Exception e) {
            System.out.println("Please install Docker Toolbox and start Docker Quick Start Terminal once, ignoring test.");
            e.printStackTrace();
            return false;
        }
    }

    private void setDocker() {
        docker = new DockerImpl(
                dockerConfig,
                false, /* fallback to 1.23 on errors */
                false, /* try setup network */
                100 /* dockerConnectTimeoutMillis */,
                new MetricReceiverWrapper(MetricReceiver.nullImplementation));
    }

    private void testReachabilityFromHost(String target) throws IOException, InterruptedException {
        URL url = new URL(target);
        String containerServer = IOUtils.toString(url.openStream());
        assertThat(containerServer, is("pong\n"));
    }

    private void createDockerTestNetworkIfNeeded() {
        if (! docker.dockerClient.listNetworksCmd().withNameFilter(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).exec().isEmpty()) return;

        Network.Ipam ipam = new Network.Ipam().withConfig(new Network.Ipam.Config().withSubnet("172.18.0.0/16"));
        docker.dockerClient.createNetworkCmd()
                .withName(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).withDriver("bridge").withIpam(ipam).exec();
    }

    private void createDockerImage() throws IOException, ExecutionException, InterruptedException {
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

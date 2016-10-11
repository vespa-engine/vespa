// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
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
 *   3. Run tests from IDE/mvn.
 *
 * LINUX:
 *  1. Remove Ignore annotations
 *  2. Change ownership of docker.sock
 *      $ sudo chown <your username> /var/run/docker.sock
 *  3. (Temporary) Manually create the docker network used by DockerImpl by running:
 *      $ sudo docker network create --ipv6 --gateway=<your local IPv6 address> --subnet=fe80::1/16 habla
 *  4. (Temporary) Manually build docker test image. Inside src/test/resources/simple-ipv6-server run:
 *      $ sudo docker build -t "simple-ipv6-server:Dockerfile" .
 *  5. (Temporary) Comment out createDockerImage() and shutdown()
 *
 * @author valerijf
 * @author dybdahl
 */
public class DockerTest {
    private DockerImpl docker;
    private static final boolean isMacOSX = System.getProperty("os.name").equals("Mac OS X");
    private static final String prefix = "/Users/" + System.getProperty("user.name") + "/.docker/machine/machines/default/";
    private static final DockerConfig dockerConfig = new DockerConfig(new DockerConfig.Builder()
            .caCertPath(isMacOSX ? prefix + "ca.pem" : "")
            .clientCertPath(isMacOSX ? prefix + "cert.pem" : "")
            .clientKeyPath(isMacOSX ? prefix + "key.pem" : "")
            .uri(isMacOSX ? "tcp://192.168.99.100:2376" : "unix:///var/run/docker.sock"));
    private static final DockerImage dockerImage = new DockerImage("simple-ipv6-server:Dockerfile");

    @Test
    public void testGetAllManagedContainersNoContainersRunning() {
        assumeTrue(isMacOSX);
        assumeTrue(dockerDaemonIsPresent());

        List<Container> containers = docker.getAllManagedContainers();
        assertThat(containers.isEmpty(), is(true));
    }

    @Test
    public void testDockerImagePull() throws ExecutionException, InterruptedException {
        assumeTrue(isMacOSX);
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
    public void testCreateImageStartAndStopContainerDeleteImage() throws IOException, InterruptedException, ExecutionException {
        assumeTrue(isMacOSX);
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

    @Ignore
    @Test
    public void testDockerNetworking() throws InterruptedException, ExecutionException, IOException {
        String hostName1 = "docker10.test.yahoo.com";
        String hostName2 = "docker11.test.yahoo.com";
        ContainerName containerName1 = new ContainerName("test-container-1");
        ContainerName containerName2 = new ContainerName("test-container-2");
        InetAddress inetAddress1 = Inet6Address.getByName("fe80::10");
        InetAddress inetAddress2 = Inet6Address.getByName("fe80::11");
        DockerImpl docker = new DockerImpl(dockerConfig, new MetricReceiverWrapper(MetricReceiver.nullImplementation));

         docker.createContainerCommand(dockerImage, containerName1, hostName1).withIpAddress(inetAddress1).create();
         docker.createContainerCommand(dockerImage, containerName2, hostName2).withIpAddress(inetAddress2).create();

        try {
            testReachabilityFromHost(containerName1, inetAddress1);
            testReachabilityFromHost(containerName2, inetAddress2);

            String[] curlFromNodeToNode = new String[]{"curl", "-g", "http://[" + inetAddress2 + "%eth0]/ping"};
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

    private boolean dockerDaemonIsPresent() {
        if (!isMacOSX) {
            System.out.println("This test does not support " + System.getProperty("os.name") + " yet, ignoring test.");
            return false;
        }
        try {
            docker = new DockerImpl(dockerConfig, new MetricReceiverWrapper(MetricReceiver.nullImplementation));
            return true;
        } catch (Exception e) {
            System.out.println("Please install Docker Toolbox and start Docker Quick Start Terminal once, ignoring test.");
            return false;
        }
    }

    private void testReachabilityFromHost(ContainerName containerName, InetAddress target) throws IOException, InterruptedException {
        String[] curlNodeFromHost = {"curl", "-g", "http://[" + target.getHostAddress() + "%" + getInterfaceName() + "]/ping"};
        while (!exec(curlNodeFromHost).equals("pong\n")) {
            Thread.sleep(20);
        }
        assertTrue("Could not reach " + containerName.asString() + " from host", exec(curlNodeFromHost).equals("pong\n"));
    }


    /**
     * Returns the display name of the bridge used by our custom docker network. This is used for routing in the
     * network tests. The bridge is assumed to be the only IPv6 interface starting with "br-"
     */
    private static String getInterfaceName() throws SocketException {
        return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .filter(networkInterface -> networkInterface.getDisplayName().startsWith("br-") &&
                        networkInterface.getInterfaceAddresses().stream()
                                .anyMatch(ip -> ip.getAddress() instanceof Inet6Address))
                .findFirst().orElseThrow(RuntimeException::new).getDisplayName();
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

    /**
     * Returns IPv6 address of on the "docker0" interface that can be reached by the containers
     */
    private static String getLocalIPv6Address() throws SocketException {
        return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .filter(networkInterface -> networkInterface.getDisplayName().equals("docker0"))
                .flatMap(i -> Collections.list(i.getInetAddresses()).stream())
                .filter(ip -> ip instanceof Inet6Address && ip.isLinkLocalAddress())
                .findFirst().orElseThrow(RuntimeException::new)
                .getHostAddress().split("%")[0];
    }

    private void createSomeNetork() throws SocketException {
        Network.Ipam ipam = new Network.Ipam().withConfig(new Network.Ipam.Config()
                .withSubnet("fe80::1/16").withGateway(getLocalIPv6Address()));
        // TODO: This needs to match the network name in DockerOperations!?
        docker.dockerClient.createNetworkCmd().withDriver("bridge").withName("habla")
                .withIpam(ipam).exec();
    }

    private void remoteNetwork() {
        // Remove the network we created earlier
        // TODO: This needs to match the network name in DockerOperations!?
        docker.dockerClient.removeNetworkCmd("habla").exec();
    }

    // TODO: Do we need this? Rather use network created by DockerImpl, right?
    void createDockerImage(DockerImpl docker) throws IOException, ExecutionException, InterruptedException {
        try {
            docker.deleteImage(new DockerImage(dockerImage.asString()));
        } catch (Exception e) {
            assertThat(e.getMessage(), is("Failed to delete docker image simple-ipv6-server:Dockerfile"));
        }

        // Build the image locally
        File dockerFilePath = new File("src/test/resources/simple-ipv6-server");
        docker.dockerClient
                .buildImageCmd(dockerFilePath)
                .withTag(dockerImage.asString()).exec(new BuildImageResultCallback()).awaitCompletion();
    }
}

// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.applicationmodel.HostName;
import org.junit.After;
import org.junit.Before;
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
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author valerijf
 */
public class DockerTest {
    /**
     * To run these tests:
     *  1. Remove Ignore annotations
     *  2. Change ownership of docker.sock
     *      $ sudo chown <your username> /var/run/docker.sock
     *  3. (Temporary) Manually create the docker network used by DockerImpl by running:
     *      $ sudo docker network create --ipv6 --gateway=<your local IPv6 address> --subnet=fe80::1/16 habla
     *  4. (Temporary) Manually build docker test image. Inside src/test/resources/simple-ipv6-server run:
     *      $ sudo docker build -t "simple-ipv6-server:Dockerfile" .
     *  5. (Temporary) Comment out setup() and shutdown()
     */
    private static final DockerConfig dockerConfig = new DockerConfig(new DockerConfig.Builder()
            .caCertPath("")     // Temporary setting it to empty as this field is required, in the future
            .clientCertPath("") // DockerConfig should be rewritten and probably moved to docker-api module
            .clientKeyPath("")
            .uri("unix:///var/run/docker.sock"));

    private static final DockerImpl docker = new DockerImpl(dockerConfig, MetricReceiver.nullImplementation);
    private static final DockerImage dockerImage = new DockerImage("simple-ipv6-server:Dockerfile");


    @Ignore
    @Test
    public void testDockerImagePull() throws ExecutionException, InterruptedException {
        DockerImage dockerImage = new DockerImage("busybox:1.24.0");

        // Pull the image and wait for the pull to complete
        docker.pullImageAsync(dockerImage).get();

        // Translate the human readable ID to sha256-hash ID that is returned by getUnusedDockerImages()
        DockerImage targetImage = new DockerImage(docker.dockerClient.inspectImageCmd(dockerImage.asString()).exec().getId());
//        assertTrue("Image: " + dockerImage + " should be unused", docker.getUnusedDockerImages().contains(targetImage));

        // Remove the image
        docker.deleteImage(dockerImage);
        assertFalse("Failed to delete " + dockerImage.asString() + " image", docker.imageIsDownloaded(dockerImage));
    }

    @Ignore
    @Test
    public void testDockerNetworking() throws InterruptedException, ExecutionException, IOException {
        HostName hostName1 = new HostName("docker10.test.yahoo.com");
        HostName hostName2 = new HostName("docker11.test.yahoo.com");
        ContainerName containerName1 = new ContainerName("test-container-1");
        ContainerName containerName2 = new ContainerName("test-container-2");
        InetAddress inetAddress1 = Inet6Address.getByName("fe80::10");
        InetAddress inetAddress2 = Inet6Address.getByName("fe80::11");

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

    private void testReachabilityFromHost(ContainerName containerName, InetAddress target) throws IOException, InterruptedException {
        String[] curlNodeFromHost = {"curl", "-g", "http://[" + target.getHostAddress() + "%" + getInterfaceName() + "]/ping"};
        while (!exec(curlNodeFromHost).equals("pong\n")) {
            Thread.sleep(20);
        }
        assertTrue("Could not reach " + containerName.asString() + " from host", exec(curlNodeFromHost).equals("pong\n"));
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

    @Before
    public void setup() throws IOException, ExecutionException, InterruptedException {
        // Build the image locally
        File dockerFilePath = new File("src/test/resources/simple-ipv6-server");
        docker.dockerClient
                .buildImageCmd(dockerFilePath)
                .withTag(dockerImage.asString()).exec(new BuildImageResultCallback()).awaitCompletion();

        // Create a temporary network
        Network.Ipam ipam = new Network.Ipam().withConfig(new Network.Ipam.Config()
                .withSubnet("fe80::1/16").withGateway(getLocalIPv6Address()));
        // TODO: This needs to match the network name in DockerOperations!?
        docker.dockerClient.createNetworkCmd().withDriver("bridge").withName("habla")
                .withIpam(ipam).exec();
    }

    @After
    public void shutdown() {
        // Remove the network we created earlier
        // TODO: This needs to match the network name in DockerOperations!?
        docker.dockerClient.removeNetworkCmd("habla").exec();
    }
}

// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * @author freva
 */
public class DockerTestUtils {
    private static final OS operatingSystem = getSystemOS();
    private static final String prefix = "/Users/" + System.getProperty("user.name") + "/.docker/machine/machines/default/";
    private static final DockerConfig dockerConfig = new DockerConfig(new DockerConfig.Builder()
            .caCertPath(    operatingSystem == OS.Mac_OS_X ? prefix + "ca.pem" : "")
            .clientCertPath(operatingSystem == OS.Mac_OS_X ? prefix + "cert.pem" : "")
            .clientKeyPath( operatingSystem == OS.Mac_OS_X ? prefix + "key.pem" : "")
            .uri(           operatingSystem == OS.Mac_OS_X ? "tcp://192.168.99.100:2376" : "tcp://localhost:2376"));
    private static DockerImpl docker;

    public static boolean dockerDaemonIsPresent() {
        if (docker != null) return true;
        if (operatingSystem == OS.Unsupported) {
            System.out.println("This test does not support " + System.getProperty("os.name") + " yet, ignoring test.");
            return false;
        }

        try {
            getDocker(); // Will throw an exception if docker is not installed/incorrectly configured
            return true;
        } catch (Exception e) {
            System.err.println("Please install Docker Toolbox and start Docker Quick Start Terminal once, ignoring test.");
            System.err.println(e.getMessage());
            return false;
        }
    }

    public static DockerImpl getDocker() {
        if (docker == null) {
            docker = new DockerImpl(
                    dockerConfig,
                    false, /* fallback to 1.23 on errors */
                    false, /* try setup network */
                    100 /* dockerConnectTimeoutMillis */,
                    new MetricReceiverWrapper(MetricReceiver.nullImplementation));
        }

        return docker;
    }

    public static void createDockerTestNetworkIfNeeded(DockerImpl docker) {
        if (! docker.dockerClient.listNetworksCmd().withNameFilter(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).exec().isEmpty()) return;

        Network.Ipam ipam = new Network.Ipam().withConfig(new Network.Ipam.Config().withSubnet("172.18.0.0/16"));
        docker.dockerClient.createNetworkCmd()
                .withName(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).withDriver("bridge").withIpam(ipam).exec();
    }

    public static void createDockerImage(DockerImpl docker, DockerImage dockerImage) throws IOException, ExecutionException, InterruptedException {
        try {
            docker.deleteImage(new DockerImage(dockerImage.asString()));
        } catch (Exception e) {
            if (! e.getMessage().equals("Failed to delete docker image " + dockerImage.asString())) {
                throw e;
            }
        }

        // Build the image locally
        File dockerFilePath = new File("src/test/resources/simple-ipv6-server");
        docker.dockerClient
                .buildImageCmd(dockerFilePath)
                .withTag(dockerImage.asString()).exec(new BuildImageResultCallback()).awaitCompletion();
    }

    private enum OS { Linux, Mac_OS_X, Unsupported }

    private static OS getSystemOS() {
        switch (System.getProperty("os.name").toLowerCase()) {
            case "linux": return OS.Linux;
            case "mac os x": return OS.Mac_OS_X;
            default: return OS.Unsupported;
        }
    }
}

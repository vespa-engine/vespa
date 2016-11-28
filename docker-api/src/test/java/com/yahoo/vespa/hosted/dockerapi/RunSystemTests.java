// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Systemtests image must be manually built because docker-java does not support --ulimit argument for docker build
 * yet (Novemeber 2016). To build, run:
 * $ sudo docker build --tag vespa-systest:latest --ulimit nofile=16384 --ulimit nproc=409600 --ulimit core=-1 docker-api/src/test/resources/systest/
 * from the root of this project.
 *
 * @author freva
 */
public class RunSystemTests {
    private static final ContainerName SYSTEM_TESTS_CONTAINER_NAME = new ContainerName("systest");
    private static final DockerImage SYSTEM_TEST_DOCKER_IMAGE = new DockerImage("vespa-systest:latest");


    public void runBasicSearch() {
        Docker docker = DockerTestUtils.getDocker();

        Path pathToSystemsTestsRepo = Paths.get("/home/valerijf/dev/systemtests/");
        startSystemTestNode(docker, "cnode-20", pathToSystemsTestsRepo);
    }

    private static void startSystemTestNode(Docker docker, String hostname, Path pathToSystemsTestsRepo) {
        try {
            InetAddress nodeInetAddress = InetAddress.getByName(hostname);

            docker.createContainerCommand(
                    SYSTEM_TEST_DOCKER_IMAGE,
                    SYSTEM_TESTS_CONTAINER_NAME,
                    hostname)
                    .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                    .withIpAddress(nodeInetAddress)
                    .withEnvironment("USER", "root")
                    .withUlimit("nofile", 16384, 16384)
                    .withUlimit("nproc", 409600, 409600)
                    .withUlimit("core", -1, -1)
                    .withVolume("/etc/hosts", "/etc/hosts")
                    .withVolume(pathToSystemsTestsRepo.toString(), "/systemtests")
                    .create();

            docker.startContainer(SYSTEM_TESTS_CONTAINER_NAME);
            docker.executeInContainer(SYSTEM_TESTS_CONTAINER_NAME, "nohup", "/systemtests/bin/node_server.rb", "&");
        } catch (UnknownHostException e) {
            throw new RuntimeException("Failed to create container " + SYSTEM_TESTS_CONTAINER_NAME.asString(), e);
        }
    }
}

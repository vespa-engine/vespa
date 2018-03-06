// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;


import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class CreateContainerCommandImplTest {

    @Test
    public void testToString() throws UnknownHostException {
        DockerImage dockerImage = new DockerImage("docker.registry.domain.tld/my/image:1.2.3");
        ContainerResources containerResources = new ContainerResources(100, 1024);
        String hostname = "docker-1.region.domain.tld";
        ContainerName containerName = ContainerName.fromHostname(hostname);

        Docker.CreateContainerCommand createContainerCommand = new CreateContainerCommandImpl(
                null, dockerImage, containerResources, containerName, hostname)
                .withLabel("my-label", "test-label")
                .withUlimit("nofile", 1, 2)
                .withUlimit("nproc", 10, 20)
                .withEnvironment("env1", "val1")
                .withEnvironment("env2", "val2")
                .withVolume("vol1", "/host/vol1")
                .withAddCapability("SYS_PTRACE")
                .withAddCapability("SYS_ADMIN")
                .withDropCapability("NET_ADMIN")
                .withNetworkMode("bridge")
                .withIpAddress(InetAddress.getByName("10.0.0.1"))
                .withIpAddress(InetAddress.getByName("::1"))
                .withEntrypoint("/path/to/program", "arg1", "arg2")
                .withPrivileged(true);

        assertEquals("--name docker-1 " +
                "--hostname docker-1.region.domain.tld " +
                "--cpu-shares 100 " +
                "--memory 1024 " +
                "--label my-label=test-label " +
                "--ulimit nofile=1:2 " +
                "--ulimit nproc=10:20 " +
                "--env env1=val1 " +
                "--env env2=val2 " +
                "--volume vol1:/host/vol1 " +
                "--cap-add SYS_ADMIN " +
                "--cap-add SYS_PTRACE " +
                "--cap-drop NET_ADMIN " +
                "--net bridge " +
                "--ip 10.0.0.1 " +
                "--ip6 0:0:0:0:0:0:0:1 " +
                "--entrypoint /path/to/program " +
                "--privileged docker.registry.domain.tld/my/image:1.2.3 " +
                "arg1 " +
                "arg2", createContainerCommand.toString());
    }

    @Test
    public void generateMacAddressTest() {
        String[][] addresses = {
                {"test123.host.yahoo.com",  null,       "abcd:1234::1", "ee:ae:a9:de:ad:c2"},
                {"test123.host.yahoo.com",  null,       "abcd:1234::2", "fa:81:11:1b:ff:fb"},
                {"unique.host.yahoo.com",   null,       "abcd:1234::1", "96:a4:00:77:90:3b"},
                {"test123.host.yahoo.com",  "10.0.0.1", null,           "7e:de:b3:7c:9e:96"},
                {"test123.host.yahoo.com",  "10.0.0.1", "abcd:1234::1", "6a:06:af:16:25:95"}};

        Stream.of(addresses).forEach(address -> {
            String generatedMac = CreateContainerCommandImpl.generateMACAddress(
                    address[0], Optional.ofNullable(address[1]), Optional.ofNullable(address[2]));
            String expectedMac = address[3];
            assertEquals(expectedMac, generatedMac);
        });
    }
}

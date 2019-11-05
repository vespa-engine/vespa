// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.yahoo.config.provision.DockerImage;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class CreateContainerCommandImplTest {

    @Test
    public void testToString() throws UnknownHostException {
        DockerImage dockerImage = DockerImage.fromString("docker.registry.domain.tld/my/image:1.2.3");
        ContainerResources containerResources = new ContainerResources(2.5, 100, 1024);
        String hostname = "docker-1.region.domain.tld";
        ContainerName containerName = ContainerName.fromHostname(hostname);

        Docker.CreateContainerCommand createContainerCommand = new CreateContainerCommandImpl(
                null, dockerImage, containerName)
                .withHostName(hostname)
                .withResources(containerResources)
                .withLabel("my-label", "test-label")
                .withUlimit("nofile", 1, 2)
                .withUlimit("nproc", 10, 20)
                .withEnvironment("env1", "val1")
                .withEnvironment("env2", "val2")
                .withVolume(Paths.get("vol1"), Paths.get("/host/vol1"))
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
                "--cpus 2.5 " +
                "--memory 1024 " +
                "--label my-label=test-label " +
                "--ulimit nofile=1:2 " +
                "--ulimit nproc=10:20 " +
                "--pids-limit -1 " +
                "--env env1=val1 " +
                "--env env2=val2 " +
                "--volume vol1:/host/vol1:Z " +
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
}

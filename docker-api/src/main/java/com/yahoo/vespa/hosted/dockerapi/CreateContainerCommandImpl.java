// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Bind;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

class CreateContainerCommandImpl implements Docker.CreateContainerCommand {
    private final DockerClient docker;
    private final DockerImage dockerImage;
    private final ContainerName containerName;
    private final String hostName;
    private final Map<String, String> labels = new HashMap<>();
    private final List<String> environmentAssignments = new ArrayList<>();
    private final List<String> volumeBindSpecs = new ArrayList<>();
    private final List<String> commands = new ArrayList<>();

    private Optional<Long> memoryInB = Optional.empty();
    private Optional<String> networkMode = Optional.empty();
    private Optional<String> ipv4Address = Optional.empty();
    private Optional<String> ipv6Address = Optional.empty();

    CreateContainerCommandImpl(DockerClient docker,
                               DockerImage dockerImage,
                               ContainerName containerName,
                               String hostName) {
        this.docker = docker;
        this.dockerImage = dockerImage;
        this.containerName = containerName;
        this.hostName = hostName;
    }

    @Override
    public Docker.CreateContainerCommand withLabel(String name, String value) {
        assert !name.contains("=");
        labels.put(name, value);
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withCmd(String name) {
        commands.add(name);
        return this;
    }


    @Override
    public Docker.CreateContainerCommand withEnvironment(String name, String value) {
        assert name.indexOf('=') == -1;
        environmentAssignments.add(name + "=" + value);
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withVolume(String path, String volumePath) {
        assert path.indexOf(':') == -1;
        volumeBindSpecs.add(path + ":" + volumePath);
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withMemoryInMb(long megaBytes) {
        memoryInB = Optional.of(megaBytes * 1024 * 1024);
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withNetworkMode(String mode) {
        networkMode = Optional.of(mode);
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withIpAddress(InetAddress address) {
        if (address instanceof Inet6Address) {
            ipv6Address = Optional.of(address.getHostAddress());
        } else {
            ipv4Address = Optional.of(address.getHostAddress());
        }
        return this;
    }

    @Override
    public void create() {
        try {
            createCreateContainerCmd().exec();
        } catch (DockerException e) {
            throw new RuntimeException("Failed to create container " + containerName.asString(), e);
        }
    }

    private CreateContainerCmd createCreateContainerCmd() {
        List<Bind> volumeBinds = volumeBindSpecs.stream().map(Bind::parse).collect(Collectors.toList());

        CreateContainerCmd containerCmd = docker
                .createContainerCmd(dockerImage.asString())
                .withName(containerName.asString())
                .withHostName(hostName)
                .withMacAddress(generateRandomMACAddress())
                .withLabels(labels)
                .withEnv(environmentAssignments)
                .withBinds(volumeBinds);

        if (! commands.isEmpty()) containerCmd = containerCmd.withCmd(commands);
        if (memoryInB.isPresent()) containerCmd = containerCmd.withMemory(memoryInB.get());
        if (networkMode.isPresent()) containerCmd = containerCmd.withNetworkMode(networkMode.get());
        if (ipv4Address.isPresent()) containerCmd = containerCmd.withIpv4Address(ipv4Address.get());
        if (ipv6Address.isPresent()) containerCmd = containerCmd.withIpv6Address(ipv6Address.get());

        return containerCmd;
    }

    /** Maps ("--env", {"A", "B", "C"}) to "--env A --env B --env C ". */
    private String toRepeatedOption(String option, List<String> optionValues) {
        StringBuilder builder = new StringBuilder();
        optionValues.stream().forEach(optionValue -> builder.append(option).append(" ").append(optionValue).append(" "));
        return builder.toString();
    }

    private String toOptionalOption(String option, Optional<?> value) {
        return value.isPresent() ? option + " " + value.get() + " " : "";
    }

    /** Make toString() print the equivalent arguments to 'docker run' */
    @Override
    public String toString() {
        List<String> labelList = labels.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.toList());

        return "--name " + containerName + " "
                + "--hostname " + hostName + " "
                + toRepeatedOption("--label", labelList)
                + toRepeatedOption("--env", environmentAssignments)
                + toRepeatedOption("--volume", volumeBindSpecs)
                + toOptionalOption("--memory", memoryInB)
                + toOptionalOption("--net", networkMode)
                + toOptionalOption("--ip", ipv4Address)
                + toOptionalOption("--ip6", ipv6Address)
                + dockerImage;
    }

    private String generateRandomMACAddress() {
        Random rand = new SecureRandom();
        byte[] macAddr = new byte[6];
        rand.nextBytes(macAddr);

        // Set second-last bit (locally administered MAC address), unset last bit (unicast)
        macAddr[0] = (byte) ((macAddr[0] | 2) & 254);
        StringBuilder sb = new StringBuilder(18);
        for (byte b : macAddr) {
            sb.append(":").append(String.format("%02x", b));
        }

        return sb.substring(1);
    }
}

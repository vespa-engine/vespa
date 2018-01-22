// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Ulimit;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class CreateContainerCommandImpl implements Docker.CreateContainerCommand {
    private final DockerClient docker;
    private final DockerImage dockerImage;
    private final ContainerResources containerResources;
    private final ContainerName containerName;
    private final String hostName;
    private final Map<String, String> labels = new HashMap<>();
    private final List<String> environmentAssignments = new ArrayList<>();
    private final List<String> volumeBindSpecs = new ArrayList<>();
    private final List<Ulimit> ulimits = new ArrayList<>();

    private Optional<String> networkMode = Optional.empty();
    private Optional<String> ipv4Address = Optional.empty();
    private Optional<String> ipv6Address = Optional.empty();
    private Optional<String[]> entrypoint = Optional.empty();
    private Set<Capability> addCapabilities = new HashSet<>();
    private Set<Capability> dropCapabilities = new HashSet<>();

    CreateContainerCommandImpl(DockerClient docker,
                               DockerImage dockerImage,
                               ContainerResources containerResources,
                               ContainerName containerName,
                               String hostName) {
        this.docker = docker;
        this.dockerImage = dockerImage;
        this.containerResources = containerResources;
        this.containerName = containerName;
        this.hostName = hostName;
    }

    @Override
    public Docker.CreateContainerCommand withLabel(String name, String value) {
        assert !name.contains("=");
        labels.put(name, value);
        return this;
    }

    public Docker.CreateContainerCommand withManagedBy(String manager) {
        labels.put(DockerImpl.LABEL_NAME_MANAGEDBY, manager);
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withAddCapability(String capabilityName) {
        addCapabilities.add(Capability.valueOf(capabilityName));
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withDropCapability(String capabilityName) {
        dropCapabilities.add(Capability.valueOf(capabilityName));
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withUlimit(String name, int softLimit, int hardLimit) {
        ulimits.add(new Ulimit(name, softLimit, hardLimit));
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withEntrypoint(String... entrypoint) {
        this.entrypoint = Optional.of(entrypoint);
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
        } catch (RuntimeException e) {
            throw new DockerException("Failed to create container " + containerName.asString(), e);
        }
    }

    private CreateContainerCmd createCreateContainerCmd() {
        List<Bind> volumeBinds = volumeBindSpecs.stream().map(Bind::parse).collect(Collectors.toList());

        final CreateContainerCmd containerCmd = docker
                .createContainerCmd(dockerImage.asString())
                .withCpuShares(containerResources.cpuShares)
                .withMemory(containerResources.memoryBytes)
                .withName(containerName.asString())
                .withHostName(hostName)
                .withLabels(labels)
                .withEnv(environmentAssignments)
                .withBinds(volumeBinds)
                .withUlimits(ulimits)
                .withCapAdd(new ArrayList<>(addCapabilities))
                .withCapDrop(new ArrayList<>(dropCapabilities));

        networkMode
                .filter(mode -> ! mode.toLowerCase().equals("host"))
                .ifPresent(mode -> containerCmd.withMacAddress(generateMACAddress(hostName, ipv4Address, ipv6Address)));

        networkMode.ifPresent(containerCmd::withNetworkMode);
        ipv4Address.ifPresent(containerCmd::withIpv4Address);
        ipv6Address.ifPresent(containerCmd::withIpv6Address);
        entrypoint.ifPresent(containerCmd::withEntrypoint);

        return containerCmd;
    }

    /** Maps ("--env", {"A", "B", "C"}) to "--env A --env B --env C ". */
    private String toRepeatedOption(String option, List<String> optionValues) {
        StringBuilder builder = new StringBuilder();
        optionValues.forEach(optionValue -> builder.append(option).append(" ").append(optionValue).append(" "));
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
        List<String> ulimitList = ulimits.stream()
                .map(ulimit -> ulimit.getName() + "=" + ulimit.getSoft() + ":" + ulimit.getHard())
                .collect(Collectors.toList());
        List<String> addCapabilitiesList = addCapabilities.stream().map(Enum<Capability>::toString).collect(Collectors.toList());
        List<String> dropCapabilitiesList = dropCapabilities.stream().map(Enum<Capability>::toString).collect(Collectors.toList());

        return "--name " + containerName.asString() + " "
                + "--hostname " + hostName + " "
                + "--cpu-shares " + containerResources.cpuShares + " "
                + "--memory " + containerResources.memoryBytes + " "
                + toRepeatedOption("--label", labelList)
                + toRepeatedOption("--ulimit", ulimitList)
                + toRepeatedOption("--env", environmentAssignments)
                + toRepeatedOption("--volume", volumeBindSpecs)
                + toRepeatedOption("--cap-add", addCapabilitiesList)
                + toRepeatedOption("--cap-drop", dropCapabilitiesList)
                + toOptionalOption("--net", networkMode)
                + toOptionalOption("--ip", ipv4Address)
                + toOptionalOption("--ip6", ipv6Address)
                + toOptionalOption("--entrypoint", entrypoint)
                + dockerImage.asString();
    }

    /**
     * Generates a pseudo-random MAC address based on the hostname, IPv4- and IPv6-address.
     */
    static String generateMACAddress(String hostname, Optional<String> ipv4Address, Optional<String> ipv6Address) {
        final String seed = hostname + ipv4Address.orElse("") + ipv6Address.orElse("");
        Random rand = getPRNG(seed);
        byte[] macAddr = new byte[6];
        rand.nextBytes(macAddr);

        // Set second-last bit (locally administered MAC address), unset last bit (unicast)
        macAddr[0] = (byte) ((macAddr[0] | 2) & 254);
        return IntStream.range(0, macAddr.length)
                .mapToObj(i -> String.format("%02x", macAddr[i]))
                .collect(Collectors.joining(":"));
    }

    private static Random getPRNG(String seed) {
        try {
            SecureRandom rand = SecureRandom.getInstance("SHA1PRNG");
            rand.setSeed(seed.getBytes());
            return rand;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to get pseudo-random number generator", e);
        }
    }
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ulimit;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.exception.DockerException;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.dockerapi.DockerImpl.LABEL_NAME_MANAGEDBY;

class CreateContainerCommandImpl implements Docker.CreateContainerCommand {

    private final DockerClient docker;
    private final DockerImage dockerImage;
    private final ContainerName containerName;
    private final Map<String, String> labels = new HashMap<>();
    private final List<String> environmentAssignments = new ArrayList<>();
    private final List<String> volumeBindSpecs = new ArrayList<>();
    private final List<String> dnsOptions = new ArrayList<>();
    private final List<Ulimit> ulimits = new ArrayList<>();
    private final Set<Capability> addCapabilities = new HashSet<>();
    private final Set<Capability> dropCapabilities = new HashSet<>();
    private final Set<String> securityOpts = new HashSet<>();

    private Optional<String> hostName = Optional.empty();
    private Optional<ContainerResources> containerResources = Optional.empty();
    private Optional<String> networkMode = Optional.empty();
    private Optional<String> ipv4Address = Optional.empty();
    private Optional<String> ipv6Address = Optional.empty();
    private Optional<String[]> entrypoint = Optional.empty();
    private boolean privileged = false;

    CreateContainerCommandImpl(DockerClient docker, DockerImage dockerImage, ContainerName containerName) {
        this.docker = docker;
        this.dockerImage = dockerImage;
        this.containerName = containerName;
    }


    @Override
    public Docker.CreateContainerCommand withHostName(String hostName) {
        this.hostName = Optional.of(hostName);
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withResources(ContainerResources containerResources) {
        this.containerResources = Optional.of(containerResources);
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withLabel(String name, String value) {
        assert !name.contains("=");
        labels.put(name, value);
        return this;
    }

    public Docker.CreateContainerCommand withManagedBy(String manager) {
        return withLabel(LABEL_NAME_MANAGEDBY, manager);
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
    public Docker.CreateContainerCommand withSecurityOpt(String securityOpt) {
        securityOpts.add(securityOpt);
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withDnsOption(String dnsOption) {
        dnsOptions.add(dnsOption);
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withPrivileged(boolean privileged) {
        this.privileged = privileged;
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withUlimit(String name, int softLimit, int hardLimit) {
        ulimits.add(new Ulimit(name, softLimit, hardLimit));
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withEntrypoint(String... entrypoint) {
        if (entrypoint.length < 1) throw new IllegalArgumentException("Entrypoint must contain at least 1 element");
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
    public Docker.CreateContainerCommand withVolume(Path path, Path volumePath) {
        volumeBindSpecs.add(path + ":" + volumePath + ":Z");
        return this;
    }

    @Override
    public Docker.CreateContainerCommand withSharedVolume(Path path, Path volumePath) {
        volumeBindSpecs.add(path + ":" + volumePath + ":z");
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
            throw new DockerException("Failed to create container " + toString(), e);
        }
    }

    private CreateContainerCmd createCreateContainerCmd() {
        List<Bind> volumeBinds = volumeBindSpecs.stream().map(Bind::parse).collect(Collectors.toList());

        final HostConfig hostConfig = new HostConfig()
                .withSecurityOpts(new ArrayList<>(securityOpts))
                .withBinds(volumeBinds)
                .withUlimits(ulimits)
                // Docker version 1.13.1 patch 94 changed default pids.max for the Docker container's cgroup
                // from max to 4096. -1L reinstates "max". File: /sys/fs/cgroup/pids/docker/CONTAINERID/pids.max.
                .withPidsLimit(-1L)
                .withCapAdd(addCapabilities.toArray(new Capability[0]))
                .withCapDrop(dropCapabilities.toArray(new Capability[0]))
                .withDnsOptions(dnsOptions)
                .withPrivileged(privileged);

        containerResources.ifPresent(cr -> hostConfig
                .withCpuShares(cr.cpuShares())
                .withMemory(cr.memoryBytes())
                // MemorySwap is the total amount of memory and swap, if MemorySwap == Memory, then container has no access swap
                .withMemorySwap(cr.memoryBytes())
                .withCpuPeriod(cr.cpuQuota() > 0 ? (long) cr.cpuPeriod() : null)
                .withCpuQuota(cr.cpuQuota() > 0 ? (long) cr.cpuQuota() : null));

        final CreateContainerCmd containerCmd = docker
                .createContainerCmd(dockerImage.asString())
                .withHostConfig(hostConfig)
                .withName(containerName.asString())
                .withLabels(labels)
                .withEnv(environmentAssignments);

        hostName.ifPresent(containerCmd::withHostName);
        networkMode.ifPresent(hostConfig::withNetworkMode);
        ipv4Address.ifPresent(containerCmd::withIpv4Address);
        ipv6Address.ifPresent(containerCmd::withIpv6Address);
        entrypoint.ifPresent(containerCmd::withEntrypoint);

        return containerCmd;
    }

    /** Maps ("--env", {"A", "B", "C"}) to "--env A --env B --env C" */
    private static String toRepeatedOption(String option, Collection<String> optionValues) {
        return optionValues.stream()
                .map(optionValue -> option + " " + optionValue)
                .collect(Collectors.joining(" "));
    }

    private static String toOptionalOption(String option, Optional<?> value) {
        return value.map(o -> option + " " + o).orElse("");
    }

    private static String toFlagOption(String option, boolean value) {
        return value ? option : "";
    }

    /** Make toString() print the equivalent arguments to 'docker run' */
    @Override
    public String toString() {
        List<String> labelList = labels.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.toList());
        List<String> ulimitList = ulimits.stream()
                .map(ulimit -> ulimit.getName() + "=" + ulimit.getSoft() + ":" + ulimit.getHard())
                .collect(Collectors.toList());
        List<String> addCapabilitiesList = addCapabilities.stream().map(Enum<Capability>::toString).sorted().collect(Collectors.toList());
        List<String> dropCapabilitiesList = dropCapabilities.stream().map(Enum<Capability>::toString).sorted().collect(Collectors.toList());
        Optional<String> entrypointExecuteable = entrypoint.map(args -> args[0]);
        String entrypointArgs = entrypoint.map(Stream::of).orElseGet(Stream::empty)
                .skip(1)
                .collect(Collectors.joining(" "));

        return Stream.of(
                "--name " + containerName.asString(),
                toOptionalOption("--hostname", hostName),
                toOptionalOption("--cpu-shares", containerResources.map(ContainerResources::cpuShares)),
                toOptionalOption("--cpus", containerResources.map(ContainerResources::cpus)),
                toOptionalOption("--memory", containerResources.map(ContainerResources::memoryBytes)),
                toRepeatedOption("--label", labelList),
                toRepeatedOption("--ulimit", ulimitList),
                "--pids-limit -1",
                toRepeatedOption("--env", environmentAssignments),
                toRepeatedOption("--volume", volumeBindSpecs),
                toRepeatedOption("--cap-add", addCapabilitiesList),
                toRepeatedOption("--cap-drop", dropCapabilitiesList),
                toRepeatedOption("--security-opt", securityOpts),
                toRepeatedOption("--dns-option", dnsOptions),
                toOptionalOption("--net", networkMode),
                toOptionalOption("--ip", ipv4Address),
                toOptionalOption("--ip6", ipv6Address),
                toOptionalOption("--entrypoint", entrypointExecuteable),
                toFlagOption("--privileged", privileged),
                dockerImage.asString(),
                entrypointArgs)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
    }
}

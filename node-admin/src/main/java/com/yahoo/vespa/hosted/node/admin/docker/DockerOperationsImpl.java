// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.google.common.net.InetAddresses;
import com.yahoo.collections.Pair;
import com.yahoo.config.provision.NodeType;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.ContainerResources;
import com.yahoo.vespa.hosted.dockerapi.ContainerStats;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeagent.ContainerData;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddresses;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Class that wraps the Docker class and have some tools related to running programs in docker.
 *
 * @author Haakon Dybdahl
 */
public class DockerOperationsImpl implements DockerOperations {

    private static final String MANAGER_NAME = "node-admin";

    private static final String IPV6_NPT_PREFIX = "fd00::";
    private static final String IPV4_NPT_PREFIX = "172.17.0.0";

    private final Docker docker;
    private final Environment environment;
    private final ProcessExecuter processExecuter;
    private final String nodeProgram;
    private final Map<Path, Boolean> directoriesToMount;

    public DockerOperationsImpl(Docker docker, Environment environment, ProcessExecuter processExecuter) {
        this.docker = docker;
        this.environment = environment;
        this.processExecuter = processExecuter;

        this.nodeProgram = environment.pathInNodeUnderVespaHome("bin/vespa-nodectl").toString();
        this.directoriesToMount = getDirectoriesToMount(environment);
    }

    @Override
    public void createContainer(ContainerName containerName, NodeSpec node, ContainerData containerData) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
        logger.info("Creating container " + containerName);

        // IPv6 - Assume always valid
        Inet6Address ipV6Address = environment.getIpAddresses().getIPv6Address(node.getHostname()).orElseThrow(
                () -> new RuntimeException("Unable to find a valid IPv6 address for " + node.getHostname() +
                        ". Missing an AAAA DNS entry?"));

        String configServers = String.join(",", environment.getConfigServerHostNames());

        Docker.CreateContainerCommand command = docker.createContainerCommand(
                node.getWantedDockerImage().get(),
                ContainerResources.from(node.getMinCpuCores(), node.getMinMainMemoryAvailableGb()),
                containerName,
                node.getHostname())
                .withManagedBy(MANAGER_NAME)
                .withEnvironment("VESPA_CONFIGSERVERS", configServers)
                .withEnvironment("CONTAINER_ENVIRONMENT_SETTINGS",
                                 environment.getContainerEnvironmentResolver().createSettings(environment, node))
                .withUlimit("nofile", 262_144, 262_144)
                .withUlimit("nproc", 32_768, 409_600)
                .withUlimit("core", -1, -1)
                .withAddCapability("SYS_PTRACE") // Needed for gcore, pstack etc.
                .withAddCapability("SYS_ADMIN"); // Needed for perf

        if (isInfrastructureHost(environment.getNodeType())) {
            command.withVolume("/var/lib/sia", "/var/lib/sia");
        }

        if (environment.getNodeType() == NodeType.proxyhost) {
            command.withVolume("/opt/yahoo/share/ssl/certs", "/opt/yahoo/share/ssl/certs");
        }

        if (environment.getNodeType() == NodeType.host) {
            Path zpePathInNode = environment.pathInNodeUnderVespaHome("var/zpe");
            if (environment.isRunningOnHost()) {
                command.withSharedVolume("/var/zpe", zpePathInNode.toString());
            } else {
                command.withVolume(environment.pathInHostFromPathInNode(containerName, zpePathInNode).toString(), zpePathInNode.toString());
            }
        }

        DockerNetworking networking = environment.getDockerNetworking();
        command.withNetworkMode(networking.getDockerNetworkMode());

        if (networking == DockerNetworking.NPT) {
            InetAddress ipV6Prefix = InetAddresses.forString(IPV6_NPT_PREFIX);
            InetAddress ipV6Local = IPAddresses.prefixTranslate(ipV6Address, ipV6Prefix, 8);
            command.withIpAddress(ipV6Local);

            // IPv4 - Only present for some containers
            Optional<InetAddress> ipV4Local = environment.getIpAddresses().getIPv4Address(node.getHostname())
                    .map(ipV4Address -> {
                        InetAddress ipV4Prefix = InetAddresses.forString(IPV4_NPT_PREFIX);
                        return IPAddresses.prefixTranslate(ipV4Address, ipV4Prefix, 2);
                    });
            ipV4Local.ifPresent(command::withIpAddress);

            addEtcHosts(containerData, node.getHostname(), ipV4Local, ipV6Local);
        }

        for (Path pathInNode : directoriesToMount.keySet()) {
            String pathInHost = environment.pathInHostFromPathInNode(containerName, pathInNode).toString();
            command.withVolume(pathInHost, pathInNode.toString());
        }

        // TODO: Enforce disk constraints
        long minMainMemoryAvailableMb = (long) (node.getMinMainMemoryAvailableGb() * 1024);
        if (minMainMemoryAvailableMb > 0) {
            // VESPA_TOTAL_MEMORY_MB is used to make any jdisc container think the machine
            // only has this much physical memory (overrides total memory reported by `free -m`).
            command.withEnvironment("VESPA_TOTAL_MEMORY_MB", Long.toString(minMainMemoryAvailableMb));
        }

        logger.info("Creating new container with args: " + command);
        command.create();
    }

    void addEtcHosts(ContainerData containerData,
                     String hostname,
                     Optional<InetAddress> ipV4Local,
                     InetAddress ipV6Local) {
        // The default /etc/hosts in a Docker container contains one entry for the host,
        // mapping the hostname to the Docker-assigned IPv4 address.
        //
        // When e.g. the cluster controller's ZooKeeper server starts, it binds the election
        // port to the localhost's IP address as returned by InetAddress.getByName, backed by
        // getaddrinfo(2), backed by that one entry in /etc/hosts. If the Docker container does
        // not have a public IPv4 address, then other members of the ZooKeeper ensemble will not
        // be able to connect.
        //
        // We will therefore explicitly manage the /etc/hosts file ourselves. Because of NPT,
        // the IP addresses needs to be local.

        StringBuilder etcHosts = new StringBuilder(
                "# This file was generated by " + DockerOperationsImpl.class.getName() + "\n" +
                "127.0.0.1\tlocalhost\n" +
                "::1\tlocalhost ip6-localhost ip6-loopback\n" +
                "fe00::0\tip6-localnet\n" +
                "ff00::0\tip6-mcastprefix\n" +
                "ff02::1\tip6-allnodes\n" +
                "ff02::2\tip6-allrouters\n" +
        ipV6Local.getHostAddress() + '\t' + hostname + '\n');
        ipV4Local.ifPresent(ipv4 -> etcHosts.append(ipv4.getHostAddress() + '\t' + hostname + '\n'));

        containerData.addFile(Paths.get("/etc/hosts"), etcHosts.toString());
    }

    @Override
    public void startContainer(ContainerName containerName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
        logger.info("Starting container " + containerName);

        docker.startContainer(containerName);

        directoriesToMount.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .forEach(path ->
                        docker.executeInContainerAsRoot(containerName, "chmod", "-R", "a+w", path.toString()));
    }

    @Override
    public void removeContainer(Container existingContainer) {
        final ContainerName containerName = existingContainer.name;
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
        if (existingContainer.state.isRunning()) {
            logger.info("Stopping container " + containerName.asString());
            docker.stopContainer(containerName);
        }

        logger.info("Deleting container " + containerName.asString());
        docker.deleteContainer(containerName);
    }

    @Override
    public Optional<Container> getContainer(ContainerName containerName) {
        return docker.getContainer(containerName);
    }

    @Override
    public boolean pullImageAsyncIfNeeded(DockerImage dockerImage) {
        return docker.pullImageAsyncIfNeeded(dockerImage);
    }

    ProcessResult executeCommandInContainer(ContainerName containerName, String... command) {
        ProcessResult result = docker.executeInContainerAsRoot(containerName, command);

        if (!result.isSuccess()) {
            throw new RuntimeException("Container " + containerName.asString() +
                    ": command " + Arrays.toString(command) + " failed: " + result);
        }
        return result;
    }

    @Override
    public ProcessResult executeCommandInContainerAsRoot(ContainerName containerName, Long timeoutSeconds, String... command) {
        return docker.executeInContainerAsRoot(containerName, timeoutSeconds, command);
    }

    @Override
    public ProcessResult executeCommandInContainerAsRoot(ContainerName containerName, String... command) {
        return docker.executeInContainerAsRoot(containerName, command);
    }

    @Override
    public ProcessResult executeCommandInNetworkNamespace(ContainerName containerName, String... command) {
        final PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
        final Integer containerPid = docker.getContainer(containerName)
                .filter(container -> container.state.isRunning())
                .map(container -> container.pid)
                .orElseThrow(() -> new RuntimeException("PID not found for container with name: " +
                        containerName.asString()));

        Path procPath = environment.getPathResolver().getPathToRootOfHost().resolve("proc");

        final String[] wrappedCommand = Stream.concat(
                Stream.of("nsenter", String.format("--net=%s/%d/ns/net", procPath, containerPid), "--"),
                Stream.of(command))
                .toArray(String[]::new);

        try {
            Pair<Integer, String> result = processExecuter.exec(wrappedCommand);
            if (result.getFirst() != 0) {
                String msg = String.format(
                        "Failed to execute %s in network namespace for %s (PID = %d), exit code: %d, output: %s",
                        Arrays.toString(wrappedCommand), containerName.asString(), containerPid, result.getFirst(), result.getSecond());
                logger.error(msg);
                throw new RuntimeException(msg);
            }
            return new ProcessResult(0, result.getSecond(), "");
        } catch (IOException e) {
            logger.warning(String.format("IOException while executing %s in network namespace for %s (PID = %d)",
                    Arrays.toString(wrappedCommand), containerName.asString(), containerPid), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void resumeNode(ContainerName containerName) {
        executeCommandInContainer(containerName, nodeProgram, "resume");
    }

    @Override
    public void suspendNode(ContainerName containerName) {
        executeCommandInContainer(containerName, nodeProgram, "suspend");
    }

    @Override
    public void restartVespa(ContainerName containerName) {
        executeCommandInContainer(containerName, nodeProgram, "restart-vespa");
    }

    @Override
    public void startServices(ContainerName containerName) {
        executeCommandInContainer(containerName, nodeProgram, "start");
    }

    @Override
    public void stopServices(ContainerName containerName) {
        executeCommandInContainer(containerName, nodeProgram, "stop");
    }


    @Override
    public Optional<ContainerStats> getContainerStats(ContainerName containerName) {
        return docker.getContainerStats(containerName);
    }

    @Override
    public List<Container> getAllManagedContainers() {
        return docker.getAllContainersManagedBy(MANAGER_NAME);
    }

    /**
     * Returns map of directories to mount and whether they should be writable by everyone
     */
    private static Map<Path, Boolean> getDirectoriesToMount(Environment environment) {
        final Map<Path, Boolean> directoriesToMount = new HashMap<>();
        directoriesToMount.put(Paths.get("/etc/yamas-agent"), true);
        directoriesToMount.put(Paths.get("/etc/filebeat"), true);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/daemontools_y"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/jdisc_core"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/langdetect/"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/vespa"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/yca"), true);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/yck"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/yell"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/ykeykey"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/ykeykeyd"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/yms_agent"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/ysar"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/ystatus"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("logs/zpu"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/cache"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/crash"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/db/jdisc"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/db/vespa"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/jdisc_container"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/jdisc_core"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/maven"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/mediasearch"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/run"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/scoreboards"), true);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/service"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/share"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/spool"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/vespa"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/yca"), true);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/ycore++"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/zookeeper"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("tmp"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/container-data"), false);
        if (environment.getNodeType() == NodeType.proxyhost)
            directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/vespa-hosted/routing"), true);
        if (environment.getNodeType() == NodeType.host)
            directoriesToMount.put(Paths.get("/var/lib/sia"), true);

        return Collections.unmodifiableMap(directoriesToMount);
    }

    /** Returns whether given nodeType is a Docker host for infrastructure nodes */
    private static boolean isInfrastructureHost(NodeType nodeType) {
        return nodeType == NodeType.confighost ||
               nodeType == NodeType.proxyhost ||
               nodeType == NodeType.controllerhost;
    }

}

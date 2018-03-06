// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.collections.Pair;
import com.yahoo.config.provision.NodeType;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.ContainerResources;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.DockerImpl;
import com.yahoo.vespa.hosted.dockerapi.DockerNetworkCreator;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.NATCommand;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddresses;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
    private static final String DOCKER_CUSTOM_BRIDGE_NETWORK_NAME = "vespa-bridge";
    
    private final Docker docker;
    private final Environment environment;
    private final ProcessExecuter processExecuter;
    private final String nodeProgram;
    private Map<Path, Boolean> directoriesToMount;
    private final IPAddresses retriever;

    public DockerOperationsImpl(Docker docker, Environment environment, ProcessExecuter processExecuter, IPAddresses retriever) {
        this.docker = docker;
        this.environment = environment;
        this.processExecuter = processExecuter;
        this.retriever = retriever;

        this.nodeProgram = environment.pathInNodeUnderVespaHome("bin/vespa-nodectl").toString();
        this.directoriesToMount = getDirectoriesToMount(environment);
    }

    @Override
    public void createContainer(ContainerName containerName, final ContainerNodeSpec nodeSpec) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
        logger.info("Creating container " + containerName);
        try {
            InetAddress nodeInetAddress = environment.getInetAddressForHost(nodeSpec.hostname);

            String configServers = environment.getConfigServerUris().stream()
                    .map(URI::getHost)
                    .collect(Collectors.joining(","));

            Docker.CreateContainerCommand command = docker.createContainerCommand(
                    nodeSpec.wantedDockerImage.get(),
                    ContainerResources.from(nodeSpec.minCpuCores, nodeSpec.minMainMemoryAvailableGb),
                    containerName,
                    nodeSpec.hostname)
                    .withManagedBy(MANAGER_NAME)
                    .withEnvironment("CONFIG_SERVER_ADDRESS", configServers) // TODO: Remove when all images support CONTAINER_ENVIRONMENT_SETTINGS
                    .withEnvironment("CONTAINER_ENVIRONMENT_SETTINGS", createContainerEnvironmentSettings(environment, nodeSpec))
                    .withUlimit("nofile", 262_144, 262_144)
                    .withUlimit("nproc", 32_768, 409_600)
                    .withUlimit("core", -1, -1)
                    .withAddCapability("SYS_PTRACE") // Needed for gcore, pstack etc.
                    .withAddCapability("SYS_ADMIN"); // Needed for perf

            if (!docker.networkNPTed()) {
                command.withIpAddress(nodeInetAddress);
                command.withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME);
                command.withVolume("/etc/hosts", "/etc/hosts"); // TODO This is probably not necessary - review later
            } else {
                // IPv6 - Assume always valid
                Inet6Address ipV6Address = this.retriever.getIPv6Address(nodeSpec.hostname).orElseThrow(
                        () -> new RuntimeException("Unable to find a valid IPv6 address. Missing an AAAA DNS entry?"));
                InetAddress ipV6Prefix = InetAddress.getByName(IPV6_NPT_PREFIX);
                InetAddress ipV6Local = IPAddresses.prefixTranslate(ipV6Address, ipV6Prefix, 8);
                command.withIpAddress(ipV6Local);

                // IPv4 - Only present for some containers
                Optional<Inet4Address> ipV4Address = this.retriever.getIPv4Address(nodeSpec.hostname);
                if (ipV4Address.isPresent()) {
                    InetAddress ipV4Prefix = InetAddress.getByName(IPV4_NPT_PREFIX);
                    InetAddress ipV4Local = IPAddresses.prefixTranslate(ipV4Address.get(), ipV4Prefix, 2);
                    command.withIpAddress(ipV4Local);
                }

                command.withNetworkMode(DOCKER_CUSTOM_BRIDGE_NETWORK_NAME);
            }

            for (Path pathInNode : directoriesToMount.keySet()) {
                String pathInHost = environment.pathInHostFromPathInNode(containerName, pathInNode).toString();
                command.withVolume(pathInHost, pathInNode.toString());
            }

            // TODO: Enforce disk constraints
            long minMainMemoryAvailableMb = (long) (nodeSpec.minMainMemoryAvailableGb * 1024);
            if (minMainMemoryAvailableMb > 0) {
                // VESPA_TOTAL_MEMORY_MB is used to make any jdisc container think the machine
                // only has this much physical memory (overrides total memory reported by `free -m`).
                command.withEnvironment("VESPA_TOTAL_MEMORY_MB", Long.toString(minMainMemoryAvailableMb));
            }

            logger.info("Creating new container with args: " + command);
            command.create();

            docker.createContainer(command);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create container " + containerName.asString(), e);
        }
    }

    @Override
    public void startContainer(ContainerName containerName, final ContainerNodeSpec nodeSpec) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
        logger.info("Starting container " + containerName);
        try {
            InetAddress nodeInetAddress = environment.getInetAddressForHost(nodeSpec.hostname);
            boolean isIPv6 = nodeInetAddress instanceof Inet6Address;

            if (isIPv6) {
                if (!docker.networkNPTed()) {
                    docker.connectContainerToNetwork(containerName, "bridge");
                }

                docker.startContainer(containerName);
                setupContainerNetworkConnectivity(containerName);
            } else {
                docker.startContainer(containerName);
            }

            directoriesToMount.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .forEach(path ->
                            docker.executeInContainerAsRoot(containerName, "chmod", "-R", "a+w", path.toString()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to start container " + containerName.asString(), e);
        }
    }

    @Override
    public void removeContainer(final Container existingContainer, ContainerNodeSpec nodeSpec) {
        final ContainerName containerName = existingContainer.name;
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
        if (existingContainer.state.isRunning()) {
            logger.info("Stopping container " + containerName.asString());
            docker.stopContainer(containerName);
        }

        logger.info("Deleting container " + containerName.asString());
        docker.deleteContainer(containerName);

        if (docker.networkNPTed()) {
            logger.info("Delete iptables NAT rules for " + containerName.asString());
            try {
                InetAddress nodeInetAddress = environment.getInetAddressForHost(nodeSpec.hostname);
                String ipv6Str = docker.getGlobalIPv6Address(containerName);
                String drop = NATCommand.drop(nodeInetAddress, InetAddress.getByName(ipv6Str));
                Pair<Integer, String> result = processExecuter.exec(drop);
                if (result.getFirst() != 0) {
                    // Might be because the one or two (out of three) rules where not present - debug log and ignore
                    // Trailing rules will always be overridden by a new one due the fact that we insert
                    logger.debug("Unable to drop NAT rule - error message: " + result.getSecond());
                }
            } catch (IOException e) {
                logger.warning("Unable to drop NAT rule for container " + containerName, e);
            }
        }
    }

    @Override
    public Optional<Container> getContainer(ContainerName containerName) {
        return docker.getContainer(containerName);
    }

    /**
     * Try to suspend node. Suspending a node means the node should be taken offline,
     * such that maintenance can be done of the node (upgrading, rebooting, etc),
     * and such that we will start serving again as soon as possible afterwards.
     * <p>
     * Any failures are logged and ignored.
     */
    @Override
    public void trySuspendNode(ContainerName containerName) {
        try {
            // TODO: Change to waiting w/o timeout (need separate thread that we can stop).
            executeCommandInContainer(containerName, nodeProgram, "suspend");
        } catch (RuntimeException e) {
            PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
            // It's bad to continue as-if nothing happened, but on the other hand if we do not proceed to
            // remove container, we will not be able to upgrade to fix any problems in the suspend logic!
            logger.warning("Failed trying to suspend container " + containerName.asString(), e);
        }
    }

    /**
     * For macvlan:
     * <p>
     * Due to a bug in docker (https://github.com/docker/libnetwork/issues/1443), we need to manually set
     * IPv6 gateway in containers connected to more than one docker network
     */
    private void setupContainerNetworkConnectivity(ContainerName containerName) throws IOException {
        if (!docker.networkNPTed()) {
            InetAddress hostDefaultGateway = DockerNetworkCreator.getDefaultGatewayLinux(true);
            executeCommandInNetworkNamespace(containerName,
                    "route", "-A", "inet6", "add", "default", "gw", hostDefaultGateway.getHostAddress(), "dev", "eth1");
        }
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
    public void executeCommandInNetworkNamespace(ContainerName containerName, String... command) {
        final PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
        final Integer containerPid = docker.getContainer(containerName)
                .filter(container -> container.state.isRunning())
                .map(container -> container.pid)
                .orElseThrow(() -> new RuntimeException("PID not found for container with name: " +
                        containerName.asString()));

        final String[] wrappedCommand = Stream.concat(
                Stream.of("sudo", "nsenter", String.format("--net=/host/proc/%d/ns/net", containerPid), "--"),
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
    public void restartVespaOnNode(ContainerName containerName) {
        executeCommandInContainer(containerName, nodeProgram, "restart-vespa");
    }

    @Override
    public void stopServicesOnNode(ContainerName containerName) {
        executeCommandInContainer(containerName, nodeProgram, "stop");
    }

    @Override
    public Optional<Docker.ContainerStats> getContainerStats(ContainerName containerName) {
        return docker.getContainerStats(containerName);
    }

    @Override
    public List<Container> getAllManagedContainers() {
        return docker.getAllContainersManagedBy(MANAGER_NAME);
    }

    @Override
    public List<ContainerName> listAllManagedContainers() {
        return docker.listAllContainersManagedBy(MANAGER_NAME);
    }

    @Override
    public void deleteUnusedDockerImages() {
        docker.deleteUnusedDockerImages();
    }

    private String createContainerEnvironmentSettings(Environment environment, ContainerNodeSpec nodeSpec) {
        ObjectMapper objectMapper = new ObjectMapper();

        ContainerEnvironmentSettings settings = new ContainerEnvironmentSettings();
        settings.set("configServerAddresses", environment.getConfigServerHostNames());
        settings.set("nodeType", nodeSpec.nodeType);

        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not write " + settings, e);
        }
    }

    private class ContainerEnvironmentSettings {
        @JsonProperty(value="environmentVariables")
        private final Map<String, Object> variables = new HashMap<>();

        void set(String argument, Object value) {
            variables.put(argument, value);
        }
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
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/run"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/scoreboards"), true);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/service"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/share"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/spool"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/vespa"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/yca"), true);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/ycore++"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/zookeeper"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/zpe"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("tmp"), false);
        directoriesToMount.put(environment.pathInNodeUnderVespaHome("var/container-data"), false);

        if (environment.getNodeType() == NodeType.confighost) {
            directoriesToMount.put(Paths.get("/var/lib/sia"), false);
        }
        
        return directoriesToMount;
    }
}

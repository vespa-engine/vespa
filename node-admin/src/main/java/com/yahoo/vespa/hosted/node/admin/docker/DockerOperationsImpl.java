// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.collections.Pair;
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
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * Class that wraps the Docker class and have some tools related to running programs in docker.
 *
 * @author dybis
 */
public class DockerOperationsImpl implements DockerOperations {
    public static final String NODE_PROGRAM = getDefaults().underVespaHome("bin/vespa-nodectl");

    private static final String[] RESUME_NODE_COMMAND = new String[]{NODE_PROGRAM, "resume"};
    private static final String[] SUSPEND_NODE_COMMAND = new String[]{NODE_PROGRAM, "suspend"};
    private static final String[] RESTART_VESPA_ON_NODE_COMMAND = new String[]{NODE_PROGRAM, "restart-vespa"};
    private static final String[] STOP_NODE_COMMAND = new String[]{NODE_PROGRAM, "stop"};

    private static final String MANAGER_NAME = "node-admin";

    // Map of directories to mount and whether they should be writable by everyone
    private static final Map<String, Boolean> DIRECTORIES_TO_MOUNT = new HashMap<>();

    static {
        DIRECTORIES_TO_MOUNT.put("/etc/yamas-agent", true);
        DIRECTORIES_TO_MOUNT.put("/etc/filebeat", true);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/daemontools_y"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/jdisc_core"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/langdetect/"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/vespa"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/yca"), true);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/yck"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/yell"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/ykeykey"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/ykeykeyd"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/yms_agent"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/ysar"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/ystatus"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/zpu"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/cache"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/crash"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/db/jdisc"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/db/vespa"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/jdisc_container"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/jdisc_core"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/maven"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/run"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/scoreboards"), true);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/service"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/share"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/spool"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/vespa"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/yca"), true);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/ycore++"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/zookeeper"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("tmp"), false);
    }

    private final Docker docker;
    private final Environment environment;
    private final ProcessExecuter processExecuter;

    public DockerOperationsImpl(Docker docker, Environment environment, ProcessExecuter processExecuter) {
        this.docker = docker;
        this.environment = environment;
        this.processExecuter = processExecuter;
    }

    @Override
    public void startContainer(ContainerName containerName, final ContainerNodeSpec nodeSpec) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);

        logger.info("Starting container " + containerName);
        try {
            InetAddress nodeInetAddress = environment.getInetAddressForHost(nodeSpec.hostname);
            final boolean isIPv6 = nodeInetAddress instanceof Inet6Address;

            String configServers = environment.getConfigServerUris().stream()
                    .map(URI::getHost)
                    .collect(Collectors.joining(","));
            Docker.CreateContainerCommand command = docker.createContainerCommand(
                    nodeSpec.wantedDockerImage.get(),
                    ContainerResources.from(nodeSpec.minCpuCores, nodeSpec.minMainMemoryAvailableGb),
                    containerName,
                    nodeSpec.hostname)
                    .withManagedBy(MANAGER_NAME)
                    .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                    .withIpAddress(nodeInetAddress)
                    .withEnvironment("CONFIG_SERVER_ADDRESS", configServers)
                    .withUlimit("nofile", 262_144, 262_144)
                    .withUlimit("nproc", 32_768, 409_600)
                    .withUlimit("core", -1, -1)
                    .withAddCapability("SYS_PTRACE") // Needed for gcore, pstack etc.
                    .withAddCapability("SYS_ADMIN"); // Needed for perf

            command.withVolume("/etc/hosts", "/etc/hosts");
            for (String pathInNode : DIRECTORIES_TO_MOUNT.keySet()) {
                String pathInHost = environment.pathInHostFromPathInNode(containerName, pathInNode).toString();
                command.withVolume(pathInHost, pathInNode);
            }

            // TODO: Enforce disk constraints
            long minMainMemoryAvailableMb = (long) (nodeSpec.minMainMemoryAvailableGb * 1024);
            if (minMainMemoryAvailableMb > 0) {
                // VESPA_TOTAL_MEMORY_MB is used to make any jdisc container think the machine
                // only has this much physical memory (overrides total memory reported by `free -m`).
                command.withEnvironment("VESPA_TOTAL_MEMORY_MB", Long.toString(minMainMemoryAvailableMb));
            }

            logger.info("Starting new container with args: " + command);
            command.create();

            if (isIPv6) {
                docker.connectContainerToNetwork(containerName, "bridge");
                docker.startContainer(containerName);
                setupContainerNetworkingWithScript(containerName);
            } else {
                docker.startContainer(containerName);
            }

            DIRECTORIES_TO_MOUNT.entrySet().stream().filter(Map.Entry::getValue).forEach(entry ->
                    docker.executeInContainerAsRoot(containerName, "chmod", "-R", "a+w", entry.getKey()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create container " + containerName.asString(), e);
        }
    }

    @Override
    public void removeContainer(final Container existingContainer) {
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
            executeCommandInContainer(containerName, SUSPEND_NODE_COMMAND);
        } catch (RuntimeException e) {
            PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
            // It's bad to continue as-if nothing happened, but on the other hand if we do not proceed to
            // remove container, we will not be able to upgrade to fix any problems in the suspend logic!
            logger.warning("Failed trying to suspend container " + containerName.asString() + "  with "
                    + Arrays.toString(SUSPEND_NODE_COMMAND), e);
        }
    }

    /**
     * Due to a bug in docker (https://github.com/docker/libnetwork/issues/1443), we need to manually set
     * IPv6 gateway in containers connected to more than one docker network
     */
    private void setupContainerNetworkingWithScript(ContainerName containerName) throws IOException {
        InetAddress hostDefaultGateway = DockerNetworkCreator.getDefaultGatewayLinux(true);
        executeCommandInNetworkNamespace(containerName,
                "route", "-A", "inet6", "add", "default", "gw", hostDefaultGateway.getHostAddress(), "dev", "eth1");
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
        executeCommandInContainer(containerName, RESUME_NODE_COMMAND);
    }

    @Override
    public void restartVespaOnNode(ContainerName containerName) {
        executeCommandInContainer(containerName, RESTART_VESPA_ON_NODE_COMMAND);
    }

    @Override
    public void stopServicesOnNode(ContainerName containerName) {
        executeCommandInContainer(containerName, STOP_NODE_COMMAND);
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
}

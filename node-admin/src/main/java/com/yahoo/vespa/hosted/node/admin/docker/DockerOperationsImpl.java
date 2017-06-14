// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerExecTimeoutException;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * Class that wraps the Docker class and have some tools related to running programs in docker.
 *
 * @author dybis
 */
public class DockerOperationsImpl implements DockerOperations {
    public static final String NODE_PROGRAM = Defaults.getDefaults().underVespaHome("bin/vespa-nodectl");

    private static final String[] RESUME_NODE_COMMAND = new String[]{NODE_PROGRAM, "resume"};
    private static final String[] SUSPEND_NODE_COMMAND = new String[]{NODE_PROGRAM, "suspend"};
    private static final String[] RESTART_VESPA_ON_NODE_COMMAND = new String[]{NODE_PROGRAM, "restart-vespa"};
    private static final String[] STOP_NODE_COMMAND = new String[]{NODE_PROGRAM, "stop"};

    private static final Pattern VESPA_VERSION_PATTERN = Pattern.compile("^(\\S*)$", Pattern.MULTILINE);

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
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs/zpe_policy_updater"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/cache"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/crash"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/db/jdisc"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/db/vespa"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/jdisc_container"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/jdisc_core"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/maven"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/run"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/scoreboards"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/service"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/share"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/spool"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/vespa"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/yca"), true);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/ycore++"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/zookeeper"), false);
    }

    private final Docker docker;
    private final Environment environment;

    public DockerOperationsImpl(Docker docker, Environment environment) {
        this.docker = docker;
        this.environment = environment;
    }

    // Returns empty if vespa version cannot be parsed.
    static Optional<String> parseVespaVersion(final String rawVespaVersion) {
        if (rawVespaVersion == null) return Optional.empty();

        final Matcher matcher = VESPA_VERSION_PATTERN.matcher(rawVespaVersion.trim());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    @Override
    public void startContainer(ContainerName containerName, final ContainerNodeSpec nodeSpec) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);

        logger.info("Starting container " + containerName);
        try {
            InetAddress nodeInetAddress = environment.getInetAddressForHost(nodeSpec.hostname);
            final boolean isIPv6 = nodeInetAddress instanceof Inet6Address;

            String configServers = String.join(",", environment.getConfigServerHosts());
            Docker.CreateContainerCommand command = docker.createContainerCommand(
                    nodeSpec.wantedDockerImage.get(),
                    containerName,
                    nodeSpec.hostname)
                    .withManagedBy(MANAGER_NAME)
                    .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                    .withIpAddress(nodeInetAddress)
                    .withEnvironment("CONFIG_SERVER_ADDRESS", configServers)
                    .withEnvironment("ATHENS_DOMAIN", environment.getAthensDomain())
                    .withUlimit("nofile", 262_144, 262_144)
                    .withUlimit("nproc", 32_768, 409_600)
                    .withUlimit("core", -1, -1)
                    .withAddCapability("SYS_PTRACE"); // Needed for gcore, pstack etc.

            if (environment.isRunningLocally()) {
                command.withEntrypoint("/usr/local/bin/start-services.sh", "--run-local");
            }

            command.withVolume("/etc/hosts", "/etc/hosts");
            for (String pathInNode : DIRECTORIES_TO_MOUNT.keySet()) {
                String pathInHost = environment.pathInHostFromPathInNode(containerName, pathInNode).toString();
                command.withVolume(pathInHost, pathInNode);
            }

            // TODO: Enforce disk constraints
            // TODO: Consider if CPU shares or quota should be set. For now we are just assuming they are
            // nicely controlled by docker.
            if (nodeSpec.minMainMemoryAvailableGb.isPresent()) {
                long minMainMemoryAvailableMb = (long) (nodeSpec.minMainMemoryAvailableGb.get() * 1024);
                if (minMainMemoryAvailableMb > 0) {
                    command.withMemoryInMb(minMainMemoryAvailableMb);
                    // VESPA_TOTAL_MEMORY_MB is used to make any jdisc container think the machine
                    // only has this much physical memory (overrides total memory reported by `free -m`).
                    command.withEnvironment("VESPA_TOTAL_MEMORY_MB", Long.toString(minMainMemoryAvailableMb));

                    // TODO: Remove once the lowest version in prod is 6.95
                    command.withEnvironment("TOTAL_MEMORY_MB", Long.toString(minMainMemoryAvailableMb));
                }
            }

            nodeSpec.minCpuCores.ifPresent(cpuShares -> command.withCpuShares((int) Math.round(10 * cpuShares)));

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

    // Returns true if scheduling download
    @Override
    public boolean shouldScheduleDownloadOfImage(final DockerImage dockerImage) {
        return !docker.imageIsDownloaded(dockerImage);
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
    public void scheduleDownloadOfImage(ContainerName containerName, DockerImage dockerImage, Runnable callback) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);

        logger.info("Schedule async download of " + dockerImage);
        final CompletableFuture<DockerImage> asyncPullResult = docker.pullImageAsync(dockerImage);
        asyncPullResult.whenComplete((image, throwable) -> {
            if (throwable != null) {
                logger.warning("Failed to pull " + dockerImage, throwable);
                return;
            }
            callback.run();
        });
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
                Stream.of("nsenter", String.format("--net=/host/proc/%d/ns/net", containerPid), "--"),
                Stream.of(command))
        .toArray(String[]::new);

        try {
            ProcessResult result = docker.executeInContainerAsRoot(new ContainerName("node-admin"), 60L, wrappedCommand);
            if (! result.isSuccess()) {
                String msg = String.format("Failed to execute %s in network namespace for %s (PID = %d)",
                        Arrays.toString(wrappedCommand), containerName.asString(), containerPid);
                logger.error(msg);
                throw new RuntimeException(msg);
            }
        } catch (DockerExecTimeoutException e) {
            logger.warning(String.format("Timed out while executing %s in network namespace for %s (PID = %d)",
                    Arrays.toString(wrappedCommand), containerName.asString(), containerPid));
            throw e;
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

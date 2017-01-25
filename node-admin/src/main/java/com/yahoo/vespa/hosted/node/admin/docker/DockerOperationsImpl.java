// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;
import com.yahoo.net.HostName;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.DockerImpl;
import com.yahoo.vespa.hosted.dockerapi.DockerNetworkCreator;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.dockerapi.metrics.Dimensions;
import com.yahoo.vespa.hosted.dockerapi.metrics.GaugeWrapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * Class that wraps the Docker class and have some tools related to running programs in docker.
 * @author dybis
 */
public class DockerOperationsImpl implements DockerOperations {
    public static final String NODE_PROGRAM = Defaults.getDefaults().underVespaHome("bin/vespa-nodectl");
    private static final String[] GET_VESPA_VERSION_COMMAND = new String[]{NODE_PROGRAM, "vespa-version"};

    private static final String[] RESUME_NODE_COMMAND = new String[] {NODE_PROGRAM, "resume"};
    private static final String[] SUSPEND_NODE_COMMAND = new String[] {NODE_PROGRAM, "suspend"};
    private static final String[] RESTART_NODE_COMMAND = new String[] {NODE_PROGRAM, "restart"};
    private static final String[] STOP_NODE_COMMAND = new String[] {NODE_PROGRAM, "stop"};

    private static final Pattern VESPA_VERSION_PATTERN = Pattern.compile("^(\\S*)$", Pattern.MULTILINE);

    private static final String MANAGER_NAME = "node-admin";

    // Map of directories to mount and whether they should be writeable by everyone
    private static final Map<String, Boolean> DIRECTORIES_TO_MOUNT = new HashMap<>();
    static {
        DIRECTORIES_TO_MOUNT.put("/etc/yamas-agent", true);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("logs"), false);
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
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/yca"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/ycore++"), false);
        DIRECTORIES_TO_MOUNT.put(getDefaults().underVespaHome("var/zookeeper"), false);
    }

    private final Docker docker;
    private final Environment environment;
    private final Consumer<List<String>> commandExecutor;
    private GaugeWrapper numberOfRunningContainersGauge;

    public DockerOperationsImpl(Docker docker, Environment environment, MetricReceiverWrapper metricReceiver) {
        this(docker, environment, metricReceiver, DockerOperationsImpl::runCommand);
    }

    DockerOperationsImpl(Docker docker, Environment environment, MetricReceiverWrapper metricReceiver, Consumer<List<String>> commandExecutor) {
        this.docker = docker;
        this.environment = environment;
        setMetrics(metricReceiver);
        this.commandExecutor = commandExecutor;
    }

    @Override
    public Optional<String> getVespaVersion(ContainerName containerName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);

        ProcessResult result = docker.executeInContainer(containerName, DockerOperationsImpl.GET_VESPA_VERSION_COMMAND);
        if (!result.isSuccess()) {
            logger.warning("Container " + containerName.asString() + ": Command "
                    + Arrays.toString(DockerOperationsImpl.GET_VESPA_VERSION_COMMAND) + " failed: " + result);
            return Optional.empty();
        }
        Optional<String> vespaVersion = parseVespaVersion(result.getOutput());
        if (vespaVersion.isPresent()) {
            return vespaVersion;
        } else {
            logger.warning("Container " + containerName.asString() + ": Failed to parse vespa version from "
                    + result.getOutput());
            return Optional.empty();
        }
    }

    // Returns empty if vespa version cannot be parsed.
    static Optional<String> parseVespaVersion(final String rawVespaVersion) {
        if (rawVespaVersion == null) return Optional.empty();

        final Matcher matcher = VESPA_VERSION_PATTERN.matcher(rawVespaVersion.trim());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    // Returns true if started
    @Override
    public boolean startContainerIfNeeded(final ContainerNodeSpec nodeSpec) {
        if (docker.getContainer(nodeSpec.hostname).isPresent()) return false;

        startContainer(nodeSpec);
        numberOfRunningContainersGauge.sample(getAllManagedContainers().size());
        return true;
    }

    // Returns true if scheduling download
    @Override
    public boolean shouldScheduleDownloadOfImage(final DockerImage dockerImage) {
        return !docker.imageIsDownloaded(dockerImage);
    }

    @Override
    public Optional<Container> getContainer(String hostname) {
        return docker.getContainer(hostname);
    }

    /**
     * Executes a program and returns its result, or if it doesn't exist, return a result
     * as-if the program executed with exit status 0 and no output.
     */
    Optional<ProcessResult> executeOptionalProgramInContainer(ContainerName containerName, String... args) {
        assert args.length > 0;
        String[] nodeProgramExistsCommand = programExistsCommand(args[0]);
        if (!docker.executeInContainer(containerName, nodeProgramExistsCommand).isSuccess()) {
            return Optional.empty();
        }

        return Optional.of(docker.executeInContainer(containerName, args));
    }

    String[] programExistsCommand(String programPath) {
        return new String[]{ "/usr/bin/env", "test", "-x", programPath };
    }

    /**
     * Try to suspend node. Suspending a node means the node should be taken offline,
     * such that maintenance can be done of the node (upgrading, rebooting, etc),
     * and such that we will start serving again as soon as possible afterwards.
     *
     * Any failures are logged and ignored.
     */
    @Override
    public void trySuspendNode(ContainerName containerName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
        Optional<ProcessResult> result;

        try {
            // TODO: Change to waiting w/o timeout (need separate thread that we can stop).
            result = executeOptionalProgramInContainer(containerName, SUSPEND_NODE_COMMAND);
        } catch (RuntimeException e) {
            // It's bad to continue as-if nothing happened, but on the other hand if we do not proceed to
            // remove container, we will not be able to upgrade to fix any problems in the suspend logic!
            logger.warning("Failed trying to suspend container " + containerName.asString() + "  with "
                   + Arrays.toString(SUSPEND_NODE_COMMAND), e);
            return;
        }

        if (result.isPresent() && !result.get().isSuccess()) {
            logger.warning("The suspend program " + Arrays.toString(SUSPEND_NODE_COMMAND)
                    + " failed: " + result.get().getOutput() + " for container " + containerName.asString());
        }
    }

    private void startContainer(final ContainerNodeSpec nodeSpec) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, nodeSpec.containerName);

        logger.info("Starting container " + nodeSpec.containerName);
        try {
            InetAddress nodeInetAddress = environment.getInetAddressForHost(nodeSpec.hostname);
            final boolean isIPv6 = nodeInetAddress instanceof Inet6Address;

            String configServers = String.join(",", environment.getConfigServerHosts());
            Docker.CreateContainerCommand command = docker.createContainerCommand(
                    nodeSpec.wantedDockerImage.get(),
                    nodeSpec.containerName,
                    nodeSpec.hostname)
                    .withManagedBy(MANAGER_NAME)
                    .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                    .withIpAddress(nodeInetAddress)
                    .withEnvironment("CONFIG_SERVER_ADDRESS", configServers)
                    .withUlimit("nofile", 16384, 16384)
                    .withUlimit("nproc", 409600, 409600)
                    .withUlimit("core", -1, -1)
                    .withAddCapability("SYS_PTRACE"); // Needed for gcore, pstack etc.

            command.withVolume("/etc/hosts", "/etc/hosts");
            for (String pathInNode : DIRECTORIES_TO_MOUNT.keySet()) {
                String pathInHost = environment.pathInHostFromPathInNode(nodeSpec.containerName, pathInNode).toString();
                command.withVolume(pathInHost, pathInNode);
            }

            // TODO: Enforce disk constraints
            // TODO: Consider if CPU shares or quoata should be set. For now we are just assuming they are
            // nicely controlled by docker.
            if (nodeSpec.minMainMemoryAvailableGb.isPresent()) {
                long minMainMemoryAvailableMb = (long) (nodeSpec.minMainMemoryAvailableGb.get() * 1024);
                if (minMainMemoryAvailableMb > 0) {
                    command.withMemoryInMb(minMainMemoryAvailableMb);
                    // TOTAL_MEMORY_MB is used to make any jdisc container think the machine
                    // only has this much physical memory (overrides total memory reported by `free -m`).
                    command.withEnvironment("TOTAL_MEMORY_MB", Long.toString(minMainMemoryAvailableMb));
                    command.withEnvironment("VESPA_TOTAL_MEMORY_MB", Long.toString(minMainMemoryAvailableMb));
                }
            }

            logger.info("Starting new container with args: " + command);
            command.create();

            if (isIPv6) {
                docker.connectContainerToNetwork(nodeSpec.containerName, "bridge");
                docker.startContainer(nodeSpec.containerName);
                setupContainerNetworkingWithScript(nodeSpec.containerName);
            } else {
                docker.startContainer(nodeSpec.containerName);
            }

            DIRECTORIES_TO_MOUNT.entrySet().stream().filter(Map.Entry::getValue).forEach(entry ->
                    docker.executeInContainer(nodeSpec.containerName, "sudo", "chmod", "-R", "a+w", entry.getKey()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create container " + nodeSpec.containerName.asString(), e);
        }
    }

    /**
     * Due to a bug in docker (https://github.com/docker/libnetwork/issues/1443), we need to manually set
     * IPv6 gateway in containers connected to more than one docker network
     */
    private void setupContainerNetworkingWithScript(ContainerName containerName) throws IOException {
        InetAddress hostDefaultGateway = DockerNetworkCreator.getDefaultGatewayLinux(true);
        executeCommandInNetworkNamespace(containerName, new String[]{
                "route", "-A", "inet6", "add", "default", "gw", hostDefaultGateway.getHostAddress(), "dev", "eth1"});
    }

    @Override
    public void scheduleDownloadOfImage(final ContainerNodeSpec nodeSpec, Runnable callback) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, nodeSpec.containerName);

        logger.info("Schedule async download of " + nodeSpec.wantedDockerImage.get());
        final CompletableFuture<DockerImage> asyncPullResult = docker.pullImageAsync(nodeSpec.wantedDockerImage.get());
        asyncPullResult.whenComplete((dockerImage, throwable) -> {
            if (throwable != null) {
                logger.warning("Failed to pull " + nodeSpec.wantedDockerImage, throwable);
                return;
            }
            assert nodeSpec.wantedDockerImage.get().equals(dockerImage);
            callback.run();
        });
    }

    @Override
    public void removeContainer(final ContainerNodeSpec nodeSpec, final Container existingContainer) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, nodeSpec.containerName);
        final ContainerName containerName = existingContainer.name;
        if (existingContainer.isRunning) {
            logger.info("Stopping container " + containerName);
            docker.stopContainer(containerName);
        }

        logger.info("Deleting container " + containerName);
        docker.deleteContainer(containerName);
        numberOfRunningContainersGauge.sample(getAllManagedContainers().size());
    }

    @Override
    public void executeCommandInContainer(ContainerName containerName, String[] command) {
        Optional<ProcessResult> result = executeOptionalProgramInContainer(containerName, command);

        if (result.isPresent() && !result.get().isSuccess()) {
            throw new RuntimeException("Container " + containerName.asString()
                                               + ": command " + Arrays.toString(command) + " failed: " + result.get());
        }
    }

    @Override
    public void executeCommandInNetworkNamespace(ContainerName containerName, String[] command) {
        final PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
        final Docker.ContainerInfo containerInfo = docker.inspectContainer(containerName)
                .orElseThrow(() -> new RuntimeException("Container " + containerName + " does not exist"));
        final Integer containerPid = containerInfo.getPid()
                .orElseThrow(() -> new RuntimeException("Container " + containerName + " isn't running (pid not found)"));

        final List<String> wrappedCommand = new LinkedList<>();
        wrappedCommand.add("sudo");
        wrappedCommand.add("-n"); // Run non-interactively and fail if a password is required
        wrappedCommand.add("nsenter");
        wrappedCommand.add(String.format("--net=/host/proc/%d/ns/net", containerPid));
        wrappedCommand.add("--");
        wrappedCommand.addAll(Arrays.asList(command));

        try {
            commandExecutor.accept(wrappedCommand);
        } catch (Exception e) {
            logger.error(String.format("Failed to execute %s in network namespace for %s (PID = %d)",
                    Arrays.toString(command), containerName.asString(), containerPid));
            throw new RuntimeException(e);
        }
    }

    @Override
    public void resumeNode(ContainerName containerName) {
        executeCommandInContainer(containerName, RESUME_NODE_COMMAND);
    }

    @Override
    public void restartServicesOnNode(ContainerName containerName) {
        executeCommandInContainer(containerName, RESTART_NODE_COMMAND);
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
    public void deleteUnusedDockerImages() {
        docker.deleteUnusedDockerImages();
    }

    private void setMetrics(MetricReceiverWrapper metricReceiver) {
        Dimensions dimensions = new Dimensions.Builder()
                .add("host", HostName.getLocalhost())
                .add("role", "docker").build();

        numberOfRunningContainersGauge = metricReceiver.declareGauge(dimensions, "containers.running");

        // Some containers could already be running, count them and initialize to that value
        numberOfRunningContainersGauge.sample(getAllManagedContainers().size());
    }

    private static void runCommand(final List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = CharStreams.toString(new InputStreamReader(process.getInputStream()));
            int resultCode = process.waitFor();
            if (resultCode != 0) {
                throw new RuntimeException("Command " + Joiner.on(' ').join(command) + " failed: " + output);
            }
        } catch (IOException|InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.DockerImpl;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;
import com.yahoo.vespa.hosted.provision.Node;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * Class that wraps the Docker class and have some tools related to running programs in docker.
 * @author dybis
 */
public class DockerOperationsImpl implements DockerOperations {
    private static final String NODE_PROGRAM = Defaults.getDefaults().vespaHome() + "bin/vespa-nodectl";
    private static final String[] GET_VESPA_VERSION_COMMAND = new String[]{NODE_PROGRAM, "vespa-version"};

    private static final String[] RESUME_NODE_COMMAND = new String[] {NODE_PROGRAM, "resume"};
    private static final String[] SUSPEND_NODE_COMMAND = new String[] {NODE_PROGRAM, "suspend"};
    private static final String[] RESTART_NODE_COMMAND = new String[] {NODE_PROGRAM, "restart"};

    private static final Pattern VESPA_VERSION_PATTERN = Pattern.compile("^(\\S*)$", Pattern.MULTILINE);

    // Map of directories to mount and whether they should be writeable by everyone
    private static final Map<String, Boolean> DIRECTORIES_TO_MOUNT = new HashMap<>();
    static {
        DIRECTORIES_TO_MOUNT.put("/metrics-share", true);
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

    public DockerOperationsImpl(Docker docker, Environment environment) {
        this.docker = docker;
        this.environment = environment;
    }

    @Override
    public String getVespaVersionOrNull(ContainerName containerName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);

        ProcessResult result = docker.executeInContainer(containerName, DockerOperationsImpl.GET_VESPA_VERSION_COMMAND);
        if (!result.isSuccess()) {
            logger.warning("Container " + containerName.asString() + ": Command "
                    + Arrays.toString(DockerOperationsImpl.GET_VESPA_VERSION_COMMAND) + " failed: " + result);
            return null;
        }
        Optional<String> vespaVersion = parseVespaVersion(result.getOutput());
        if (vespaVersion.isPresent()) {
            return vespaVersion.get();
        } else {
            logger.warning("Container " + containerName.asString() + ": Failed to parse vespa version from "
                    + result.getOutput());
            return null;
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
        final Optional<Container> existingContainer = docker.getContainer(nodeSpec.hostname);
        if (!existingContainer.isPresent()) {
            startContainer(nodeSpec);
            configureContainer(nodeSpec);
            return true;
        } else {
            return false;
        }
    }

    private void configureContainer(ContainerNodeSpec nodeSpec) {
        final Path yamasAgentFolder = Paths.get("/etc/yamas-agent/");

        Path diskUsageCheckPath = Paths.get("/bin/cat");
        Path diskUsageCheckSchedulePath = yamasAgentFolder.resolve("disk-usage.yaml");
        String diskUsageCheckSchedule = generateSecretAgentSchedule(nodeSpec, "disk-usage", 60, diskUsageCheckPath,
                "/metrics-share/disk.usage");

        Path vespaCheckPath = Paths.get("/home/y/libexec/yms/yms_check_vespa");
        Path vespaCheckSchedulePath = yamasAgentFolder.resolve("vespa.yaml");
        String vespaCheckSchedule = generateSecretAgentSchedule(nodeSpec, "vespa", 60, vespaCheckPath, "all");
        try {
            writeSecretAgentSchedule(nodeSpec.containerName, diskUsageCheckSchedulePath, diskUsageCheckSchedule);
            writeSecretAgentSchedule(nodeSpec.containerName, vespaCheckSchedulePath, vespaCheckSchedule);
        } catch (IOException e) {
            e.printStackTrace();
        }

        docker.executeInContainer(nodeSpec.containerName, "service", "yamas-agent", "restart");
    }

    private void writeSecretAgentSchedule(ContainerName containerName, Path schedulePath, String secretAgentSchedule) throws IOException {
        Path scheduleFilePath = Maintainer.pathInNodeAdminFromPathInNode(containerName, schedulePath.toString());
        Files.write(scheduleFilePath, secretAgentSchedule.getBytes());
        scheduleFilePath.toFile().setReadable(true, false); // Give everyone read access to the schedule file
    }

    String generateSecretAgentSchedule(ContainerNodeSpec nodeSpec, String id, int interval, Path pathToCheck,
                                       String... args) {
        StringBuilder stringBuilder = new StringBuilder()
                .append("- id: ").append(id).append("\n")
                .append("  interval: ").append(interval).append("\n")
                .append("  user: nobody").append("\n")
                .append("  check: ").append(pathToCheck.toFile().getAbsolutePath()).append("\n");

        if (args.length > 0) {
            stringBuilder.append("  args: \n");
            for (String arg : args) {
                stringBuilder.append("    - ").append(arg).append("\n");
            }
        }

        stringBuilder.append("  tags:\n").append("    namespace: Vespa\n");
        if (nodeSpec.owner.isPresent()) {
            stringBuilder
                    .append("    tenantName: ").append(nodeSpec.owner.get().tenant).append("\n")
                    .append("    app: ").append(nodeSpec.owner.get().application).append(".").append(nodeSpec.owner.get().instance).append("\n");
        }

        if (nodeSpec.membership.isPresent()) {
            stringBuilder
                    .append("    clustertype: ").append(nodeSpec.membership.get().clusterType).append("\n")
                    .append("    clusterid: ").append(nodeSpec.membership.get().clusterId).append("\n");
        }

        if (nodeSpec.vespaVersion.isPresent())
            stringBuilder.append("    vespaVersion: ").append(nodeSpec.vespaVersion.get()).append("\n");

        stringBuilder
                .append("    role: tenants\n")
                .append("    flavor: ").append(nodeSpec.nodeFlavor).append("\n")
                .append("    state: ").append(nodeSpec.nodeState).append("\n")
                .append("    zone: ").append(environment.getZone()).append("\n");

        return stringBuilder.toString();
    }

    // Returns true if scheduling download
    @Override
    public boolean shouldScheduleDownloadOfImage(final DockerImage dockerImage) {
        return !docker.imageIsDownloaded(dockerImage);
    }

    @Override
    public boolean removeContainerIfNeeded(ContainerNodeSpec nodeSpec, String hostname, Orchestrator orchestrator)
            throws Exception {
        Optional<Container> existingContainer = docker.getContainer(hostname);
        if (! existingContainer.isPresent()) {
            return true;
        }

        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, nodeSpec.containerName);
        Optional<String> removeReason = shouldRemoveContainer(nodeSpec, existingContainer);
        if (removeReason.isPresent()) {
            logger.info("Will remove container " + existingContainer.get() + ": " + removeReason.get());
            removeContainer(nodeSpec, existingContainer.get(), orchestrator);
            return true;
        }
        Optional<String> restartReason = shouldRestartContainer(nodeSpec);
        if (restartReason.isPresent()) {
            logger.info("Will restart container " + existingContainer.get() + ": " + restartReason.get());
            restartContainer(nodeSpec, existingContainer.get(), orchestrator);
            return true;
        }

        return false;
    }

    private Optional<String> shouldRestartContainer(ContainerNodeSpec nodeSpec) {
        if (nodeSpec.currentRestartGeneration.get() < nodeSpec.wantedRestartGeneration.get()) {
            return Optional.of("Restart requested - wanted restart generation has been bumped: "
                                       + nodeSpec.currentRestartGeneration.get() + " -> " + nodeSpec.wantedRestartGeneration
                    .get());
        }
        return Optional.empty();
    }

    private Optional<String> shouldRemoveContainer(ContainerNodeSpec nodeSpec, Optional<Container> existingContainer) {
        if (nodeSpec.nodeState != Node.State.active) {
            return Optional.of("Node no longer active");
        }
        if (!nodeSpec.wantedDockerImage.get().equals(existingContainer.get().image)) {
            return Optional.of("The node is supposed to run a new Docker image: "
                    + existingContainer.get() + " -> " + nodeSpec.wantedDockerImage.get());
        }
        if (!existingContainer.get().isRunning) {
            return Optional.of("Container no longer running");
        }
        return Optional.empty();
    }

    /**
     * Executes a program and returns its result, or if it doesn't exist, return a result
     * as-if the program executed with exit status 0 and no output.
     */
    Optional<ProcessResult> executeOptionalProgram(ContainerName containerName, String... args) {
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
    private void trySuspendNode(ContainerName containerName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
        Optional<ProcessResult> result;

        try {
            // TODO: Change to waiting w/o timeout (need separate thread that we can stop).
            result = executeOptionalProgram(containerName, SUSPEND_NODE_COMMAND);
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

            String configServers = environment.getConfigServerHosts().stream().collect(Collectors.joining(","));
            Docker.CreateContainerCommand command = docker.createContainerCommand(
                    nodeSpec.wantedDockerImage.get(),
                    nodeSpec.containerName,
                    nodeSpec.hostname)
                    .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                    .withIpAddress(nodeInetAddress)
                    .withEnvironment("CONFIG_SERVER_ADDRESS", configServers);

            command.withVolume("/etc/hosts", "/etc/hosts");
            for (String pathInNode : DIRECTORIES_TO_MOUNT.keySet()) {
                String pathInHost = Maintainer.pathInHostFromPathInNode(nodeSpec.containerName, pathInNode).toString();
                command = command.withVolume(pathInHost, pathInNode);
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
                }
            }

            logger.info("Starting new container with args: " + command);
            command.create();

            if (isIPv6) {
                docker.connectContainerToNetwork(nodeSpec.containerName, "bridge");
                docker.startContainer(nodeSpec.containerName);
                setupContainerNetworkingWithScript(nodeSpec.containerName, nodeSpec.hostname);
            } else {
                docker.startContainer(nodeSpec.containerName);
            }

            DIRECTORIES_TO_MOUNT.entrySet().stream().filter(Map.Entry::getValue).forEach(entry ->
                    docker.executeInContainer(nodeSpec.containerName, "sudo", "chmod", "-R", "a+w", entry.getKey()));
        } catch (UnknownHostException e) {
            throw new RuntimeException("Failed to create container " + nodeSpec.containerName.asString(), e);
        }
    }

    private void setupContainerNetworkingWithScript(ContainerName containerName, String hostName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);

        Docker.ContainerInfo containerInfo = docker.inspectContainer(containerName);
        Optional<Integer> containerPid = containerInfo.getPid();
        if (!containerPid.isPresent()) {
            throw new RuntimeException("Container " + containerName + " for host "
                    + hostName + " isn't running (pid not found)");
        }

        final List<String> command = new LinkedList<>();
        command.add("sudo");
        command.add(getDefaults().underVespaHome("libexec/vespa/node-admin/configure-container-networking.py"));
        command.add("--fix-docker-gateway");
        command.add(containerPid.get().toString());

        for (int retry = 0; retry < 30; ++retry) {
            try {
                runCommand(command);
                logger.info("Done setting up network");
                return;
            } catch (Exception e) {
                final int sleepSecs = 3;
                logger.warning("Failed to configure network with command " + command
                        + ", will retry in " + sleepSecs + " seconds", e);
                try {
                    Thread.sleep(sleepSecs * 1000);
                } catch (InterruptedException e1) {
                    logger.warning("Sleep interrupted", e1);
                }
            }
        }
    }

    private void runCommand(final List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        String output = CharStreams.toString(new InputStreamReader(process.getInputStream()));
        int resultCode = process.waitFor();
        if (resultCode != 0) {
            throw new Exception("Command " + Joiner.on(' ').join(command) + " failed: " + output);
        }
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

    private void removeContainer(final ContainerNodeSpec nodeSpec, final Container existingContainer, Orchestrator orchestrator)
            throws Exception {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, nodeSpec.containerName);
        final ContainerName containerName = existingContainer.name;
        if (existingContainer.isRunning) {
            // If we're stopping the node only to upgrade or restart the node or similar, we need to suspend
            // the services.
            if (nodeSpec.nodeState == Node.State.active) {
                orchestratorSuspendNode(orchestrator, nodeSpec, logger);
                trySuspendNode(containerName);
            }

            logger.info("Stopping container " + containerName);
            docker.stopContainer(containerName);
        }

        logger.info("Deleting container " + containerName);
        docker.deleteContainer(containerName);
    }

    private void restartContainer(ContainerNodeSpec nodeSpec, Container existingContainer, Orchestrator orchestrator)
            throws Exception {
        if (existingContainer.isRunning) {
            ContainerName containerName = existingContainer.name;
            PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, containerName);
            if (nodeSpec.nodeState == Node.State.active) {
                logger.info("Restarting container " + containerName);
                // Since we are restarting the node we need to suspend the services.
                orchestratorSuspendNode(orchestrator, nodeSpec, logger);
                executeCommand(containerName, RESTART_NODE_COMMAND);
            }
        }
    }

    // TODO: Also skip orchestration if we're downgrading in test/staging
    // How to implement:
    //  - test/staging: We need to figure out whether we're in test/staging, zone is available in Environment
    //  - downgrading: Impossible to know unless we look at the hosted version, which is
    //    not available in the docker image (nor its name). Not sure how to solve this. Should
    //    the node repo return the hosted version or a downgrade bit in addition to
    //    wanted docker image etc?
    // Should the tenant pipeline instead use BCP tool to upgrade faster!?
    //
    // More generally, the node repo response should contain sufficient info on what the docker image is,
    // to allow the node admin to make decisions that depend on the docker image. Or, each docker image
    // needs to contain routines for drain and suspend. For many images, these can just be dummy routines.
    private void orchestratorSuspendNode(Orchestrator orchestrator, ContainerNodeSpec nodeSpec, PrefixLogger logger) throws OrchestratorException {
        final String hostname = nodeSpec.hostname;
        logger.info("Ask Orchestrator for permission to suspend node " + hostname);
        if ( ! orchestrator.suspend(hostname)) {
            logger.info("Orchestrator rejected suspend of node " + hostname);
            // TODO: change suspend() to throw an exception if suspend is denied
            throw new OrchestratorException("Failed to get permission to suspend " + hostname);
        }
    }

    public void executeCommand(ContainerName containerName, String[] command) {
        Optional<ProcessResult> result = executeOptionalProgram(containerName, command);

        if (result.isPresent() && !result.get().isSuccess()) {
            throw new RuntimeException("Container " + containerName.asString()
                                               + ": command " + Arrays.toString(command) + " failed: " + result.get());
        }
    }

    @Override
    public void executeResume(ContainerName containerName) {
        executeCommand(containerName, RESUME_NODE_COMMAND);
    }
}

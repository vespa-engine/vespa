// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;

import java.io.File;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
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

    private static final Pattern VESPA_VERSION_PATTERN = Pattern.compile("^(\\S*)$", Pattern.MULTILINE);

    private static final List<String> DIRECTORIES_TO_MOUNT = Arrays.asList(
            getDefaults().underVespaHome("logs"),
            getDefaults().underVespaHome("var/cache"),
            getDefaults().underVespaHome("var/crash"),
            getDefaults().underVespaHome("var/db/jdisc"),
            getDefaults().underVespaHome("var/db/vespa"),
            getDefaults().underVespaHome("var/jdisc_container"),
            getDefaults().underVespaHome("var/jdisc_core"),
            getDefaults().underVespaHome("var/maven"),
            getDefaults().underVespaHome("var/run"),
            getDefaults().underVespaHome("var/scoreboards"),
            getDefaults().underVespaHome("var/service"),
            getDefaults().underVespaHome("var/share"),
            getDefaults().underVespaHome("var/spool"),
            getDefaults().underVespaHome("var/vespa"),
            getDefaults().underVespaHome("var/yca"),
            getDefaults().underVespaHome("var/ycore++"),
            getDefaults().underVespaHome("var/zookeeper"));

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
            return true;
        } else {
            return false;
        }
    }

    // Returns true if scheduling download
    @Override
    public boolean shouldScheduleDownloadOfImage(final DockerImage dockerImage) {
        return !docker.imageIsDownloaded(dockerImage);
    }

    @Override
    public boolean removeContainerIfNeeded(ContainerNodeSpec nodeSpec, HostName hostname, Orchestrator orchestrator)
            throws Exception {
        Optional<Container> existingContainer = docker.getContainer(hostname);
        if (! existingContainer.isPresent()) {
            return true;
        }
        Optional<String> removeReason = shouldRemoveContainer(nodeSpec, existingContainer);
        if (removeReason.isPresent()) {
            PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, nodeSpec.containerName);
            logger.info("Will remove container " + existingContainer.get() + ": " + removeReason.get());
            removeContainer(nodeSpec, existingContainer.get(), orchestrator);
            return true;
        }
        return false;
    }

    private Optional<String> shouldRemoveContainer(ContainerNodeSpec nodeSpec, Optional<Container> existingContainer) {
        if (nodeSpec.nodeState != NodeState.ACTIVE) {
            return Optional.of("Node no longer active");
        }
        if (!nodeSpec.wantedDockerImage.get().equals(existingContainer.get().image)) {
            return Optional.of("The node is supposed to run a new Docker image: "
                    + existingContainer.get() + " -> " + nodeSpec.wantedDockerImage.get());
        }
        if (nodeSpec.currentRestartGeneration.get() < nodeSpec.wantedRestartGeneration.get()) {
            return Optional.of("Restart requested - wanted restart generation has been bumped: "
                    + nodeSpec.currentRestartGeneration.get() + " -> " + nodeSpec.wantedRestartGeneration.get());
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

    void startContainer(final ContainerNodeSpec nodeSpec) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerOperationsImpl.class, nodeSpec.containerName);

        logger.info("Starting container " + nodeSpec.containerName);
        try {
            InetAddress nodeInetAddress = environment.getInetAddressForHost(nodeSpec.hostname.s());
            String nodeIpAddress = nodeInetAddress.getHostAddress();
            final boolean useDockerNetworking = nodeInetAddress instanceof Inet6Address && !useNetworkScriptForIpv6();

            String configServers = environment.getConfigServerHosts().stream().map(HostName::toString).collect(Collectors.joining(","));
            Docker.StartContainerCommand command = docker.createStartContainerCommand(
                    nodeSpec.wantedDockerImage.get(),
                    nodeSpec.containerName,
                    nodeSpec.hostname);
            command.withEnvironment("CONFIG_SERVER_ADDRESS", configServers);

            command.withVolume("/etc/hosts", "/etc/hosts");
            for (String pathInNode : DIRECTORIES_TO_MOUNT) {
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
                }
            }

            //If container's IP address is IPv6, use our custom network mode and let docker handle networking.
            //If container's IP address is IPv4, set up networking manually as before. In the future docker should
            //set up the IPv4 network as well.
            if (useDockerNetworking) {
                // TODO: What's "habla"? Can we change to anything here, or it seems "none" is reserved.
                command.withNetworkMode("habla").withIpv6Address(nodeIpAddress);
            } else {
                command.withNetworkMode("none");
            }

            logger.info("Starting new container with args: " + command);
            command.start();

            if (!useDockerNetworking) {
                setupContainerNetworkingWithScript(nodeSpec.containerName, nodeSpec.hostname, nodeIpAddress);
            }
        } catch (UnknownHostException e) {
            throw new RuntimeException("Failed to start container " + nodeSpec.containerName.asString(), e);
        }
    }

    private boolean useNetworkScriptForIpv6() {
        // TODO: Remove: Either always use network script for IPv6, or never
        return new File("/tmp/use-network-script.marker").exists();
    }

    private void setupContainerNetworkingWithScript(
            ContainerName containerName,
            HostName hostName,
            String ipAddress) {
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

        Environment.NetworkType networkType = environment.networkType();
        if (networkType != Environment.NetworkType.normal) {
            command.add("--" + networkType);
        }
        command.add(containerPid.get().toString());
        command.add(ipAddress);

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

        logger.info("Schedule async download of Docker image " + nodeSpec.wantedDockerImage.get());
        final CompletableFuture<DockerImage> asyncPullResult = docker.pullImageAsync(nodeSpec.wantedDockerImage.get());
        asyncPullResult.whenComplete((dockerImage, throwable) -> {
            if (throwable != null) {
                logger.warning("Failed to pull docker image " + nodeSpec.wantedDockerImage, throwable);
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
            if (nodeSpec.nodeState == NodeState.ACTIVE) {
                // TODO: Also skip orchestration if we're downgrading in test/staging
                // How to implement:
                //  - test/staging: We need to figure out whether we're in test/staging, by asking Chef!? Or,
                //    let the Orchestrator handle it - it may know what zone we're in.
                //  - downgrading: Impossible to know unless we look at the hosted version, which is
                //    not available in the docker image (nor its name). Not sure how to solve this. Should
                //    the node repo return the hosted version or a downgrade bit in addition to
                //    wanted docker image etc?
                // Should the tenant pipeline instead use BCP tool to upgrade faster!?
                //
                // More generally, the node repo response should contain sufficient info on what the docker image is,
                // to allow the node admin to make decisions that depend on the docker image. Or, each docker image
                // needs to contain routines for drain and suspend. For many image, these can just be dummy routines.

                logger.info("Ask Orchestrator for permission to suspend node " + nodeSpec.hostname);
                final boolean suspendAllowed = orchestrator.suspend(nodeSpec.hostname);
                if (!suspendAllowed) {
                    logger.info("Orchestrator rejected suspend of node");
                    // TODO: change suspend() to throw an exception if suspend is denied
                    throw new OrchestratorException("Failed to get permission to suspend " + nodeSpec.hostname);
                }

                trySuspendNode(containerName);
            }

            logger.info("Stopping container " + containerName);
            docker.stopContainer(containerName);
        }

        logger.info("Deleting container " + containerName);
        docker.deleteContainer(containerName);
    }


    @Override
    public void executeResume(ContainerName containerName) {
        Optional<ProcessResult> result = executeOptionalProgram(containerName, RESUME_NODE_COMMAND);

        if (result.isPresent() && !result.get().isSuccess()) {
            throw new RuntimeException("Container " +containerName.asString()
                    + ": command " + Arrays.toString(RESUME_NODE_COMMAND) + " failed: " + result.get());
        }
    }
}

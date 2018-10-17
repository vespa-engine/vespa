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
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeagent.ContainerData;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddresses;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Class that wraps the Docker class and have some tools related to running programs in docker.
 *
 * @author Haakon Dybdahl
 */
public class DockerOperationsImpl implements DockerOperations {

    private static final Logger logger = Logger.getLogger(DockerOperationsImpl.class.getName());

    private static final String MANAGER_NAME = "node-admin";

    private static final String IPV6_NPT_PREFIX = "fd00::";
    private static final String IPV4_NPT_PREFIX = "172.17.0.0";

    private final Docker docker;
    private final Environment environment;
    private final ProcessExecuter processExecuter;

    public DockerOperationsImpl(Docker docker, Environment environment, ProcessExecuter processExecuter) {
        this.docker = docker;
        this.environment = environment;
        this.processExecuter = processExecuter;
    }

    @Override
    public void createContainer(NodeAgentContext context, NodeSpec node, ContainerData containerData) {
        context.log(logger, "Creating container");

        // IPv6 - Assume always valid
        Inet6Address ipV6Address = environment.getIpAddresses().getIPv6Address(node.getHostname()).orElseThrow(
                () -> new RuntimeException("Unable to find a valid IPv6 address for " + node.getHostname() +
                        ". Missing an AAAA DNS entry?"));

        String configServers = String.join(",", environment.getConfigServerHostNames());

        Docker.CreateContainerCommand command = docker.createContainerCommand(
                node.getWantedDockerImage().get(),
                ContainerResources.from(node.getMinCpuCores(), node.getMinMainMemoryAvailableGb()),
                context.containerName(),
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

        addMounts(context, command);

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
    public void startContainer(NodeAgentContext context) {
        context.log(logger, "Starting container");
        docker.startContainer(context.containerName());
    }

    @Override
    public void removeContainer(NodeAgentContext context, Container container) {
        if (container.state.isRunning()) {
            context.log(logger, "Stopping container");
            docker.stopContainer(context.containerName());
        }

        context.log(logger, "Deleting container");
        docker.deleteContainer(context.containerName());
    }

    @Override
    public Optional<Container> getContainer(NodeAgentContext context) {
        return docker.getContainer(context.containerName());
    }

    @Override
    public boolean pullImageAsyncIfNeeded(DockerImage dockerImage) {
        return docker.pullImageAsyncIfNeeded(dockerImage);
    }

    @Override
    public ProcessResult executeCommandInContainerAsRoot(NodeAgentContext context, Long timeoutSeconds, String... command) {
        return docker.executeInContainerAsUser(context.containerName(), "root", OptionalLong.of(timeoutSeconds), command);
    }

    @Override
    public ProcessResult executeCommandInContainerAsRoot(NodeAgentContext context, String... command) {
        return docker.executeInContainerAsUser(context.containerName(), "root", OptionalLong.empty(), command);
    }

    @Override
    public ProcessResult executeCommandInNetworkNamespace(ContainerName containerName, String... command) {
        final Integer containerPid = docker.getContainer(containerName)
                .filter(container -> container.state.isRunning())
                .map(container -> container.pid)
                .orElseThrow(() -> new RuntimeException("PID not found for container with name: " +
                        containerName.asString()));

        final String[] wrappedCommand = Stream.concat(
                Stream.of("nsenter", String.format("--net=/proc/%d/ns/net", containerPid), "--"),
                Stream.of(command))
                .toArray(String[]::new);

        try {
            Pair<Integer, String> result = processExecuter.exec(wrappedCommand);
            if (result.getFirst() != 0) {
                throw new RuntimeException(String.format(
                        "Failed to execute %s in network namespace for %s (PID = %d), exit code: %d, output: %s",
                        Arrays.toString(wrappedCommand), containerName.asString(), containerPid, result.getFirst(), result.getSecond()));
            }
            return new ProcessResult(0, result.getSecond(), "");
        } catch (IOException e) {
            throw new RuntimeException(String.format("IOException while executing %s in network namespace for %s (PID = %d)",
                    Arrays.toString(wrappedCommand), containerName.asString(), containerPid), e);
        }
    }

    @Override
    public void resumeNode(NodeAgentContext context) {
        executeNodeCtlInContainer(context, "resume");
    }

    @Override
    public void suspendNode(NodeAgentContext context) {
        executeNodeCtlInContainer(context, "suspend");
    }

    @Override
    public void restartVespa(NodeAgentContext context) {
        executeNodeCtlInContainer(context, "restart-vespa");
    }

    @Override
    public void startServices(NodeAgentContext context) {
        executeNodeCtlInContainer(context, "start");
    }

    @Override
    public void stopServices(NodeAgentContext context) {
        executeNodeCtlInContainer(context, "stop");
    }

    ProcessResult executeNodeCtlInContainer(NodeAgentContext context, String program) {
        String[] command = new String[] {context.pathInNodeUnderVespaHome("bin/vespa-nodectl").toString(), program};
        ProcessResult result = executeCommandInContainerAsRoot(context, command);

        if (!result.isSuccess()) {
            throw new RuntimeException("Container " + context.containerName().asString() +
                    ": command " + Arrays.toString(command) + " failed: " + result);
        }
        return result;
    }


    @Override
    public Optional<ContainerStats> getContainerStats(NodeAgentContext context) {
        return docker.getContainerStats(context.containerName());
    }

    @Override
    public List<Container> getAllManagedContainers() {
        return docker.getAllContainersManagedBy(MANAGER_NAME);
    }

    /**
     * Returns map of directories to mount and whether they should be writable by everyone
     */
    private static void addMounts(NodeAgentContext context, Docker.CreateContainerCommand command) {
        final Path varLibSia = Paths.get("/var/lib/sia");

        // Paths unique to each container
        List<Path> paths = new ArrayList<>(Arrays.asList(
                Paths.get("/etc/yamas-agent"),
                Paths.get("/etc/filebeat"),
                context.pathInNodeUnderVespaHome("logs/daemontools_y"),
                context.pathInNodeUnderVespaHome("logs/jdisc_core"),
                context.pathInNodeUnderVespaHome("logs/langdetect/"),
                context.pathInNodeUnderVespaHome("logs/nginx"),
                context.pathInNodeUnderVespaHome("logs/vespa"),
                context.pathInNodeUnderVespaHome("logs/yca"),
                context.pathInNodeUnderVespaHome("logs/yck"),
                context.pathInNodeUnderVespaHome("logs/yell"),
                context.pathInNodeUnderVespaHome("logs/ykeykey"),
                context.pathInNodeUnderVespaHome("logs/ykeykeyd"),
                context.pathInNodeUnderVespaHome("logs/yms_agent"),
                context.pathInNodeUnderVespaHome("logs/ysar"),
                context.pathInNodeUnderVespaHome("logs/ystatus"),
                context.pathInNodeUnderVespaHome("logs/zpu"),
                context.pathInNodeUnderVespaHome("var/cache"),
                context.pathInNodeUnderVespaHome("var/crash"),
                context.pathInNodeUnderVespaHome("var/db/jdisc"),
                context.pathInNodeUnderVespaHome("var/db/vespa"),
                context.pathInNodeUnderVespaHome("var/jdisc_container"),
                context.pathInNodeUnderVespaHome("var/jdisc_core"),
                context.pathInNodeUnderVespaHome("var/maven"),
                context.pathInNodeUnderVespaHome("var/mediasearch"),
                context.pathInNodeUnderVespaHome("var/run"),
                context.pathInNodeUnderVespaHome("var/scoreboards"),
                context.pathInNodeUnderVespaHome("var/service"),
                context.pathInNodeUnderVespaHome("var/share"),
                context.pathInNodeUnderVespaHome("var/spool"),
                context.pathInNodeUnderVespaHome("var/vespa"),
                context.pathInNodeUnderVespaHome("var/yca"),
                context.pathInNodeUnderVespaHome("var/ycore++"),
                context.pathInNodeUnderVespaHome("var/zookeeper"),
                context.pathInNodeUnderVespaHome("tmp"),
                context.pathInNodeUnderVespaHome("var/container-data")));

        if (context.nodeType() == NodeType.proxyhost)
            paths.add(context.pathInNodeUnderVespaHome("var/vespa-hosted/routing"));
        if (context.nodeType() == NodeType.host)
            paths.add(varLibSia);

        paths.forEach(path -> command.withVolume(context.pathOnHostFromPathInNode(path), path));


        // Shared paths
        if (isInfrastructureHost(context.nodeType()))
            command.withSharedVolume(varLibSia, varLibSia);

        if (context.nodeType() == NodeType.proxyhost || context.nodeType() == NodeType.controllerhost)
            command.withSharedVolume(Paths.get("/opt/yahoo/share/ssl/certs"), Paths.get("/opt/yahoo/share/ssl/certs"));

        if (context.nodeType() == NodeType.host)
            command.withSharedVolume(Paths.get("/var/zpe"), context.pathInNodeUnderVespaHome("var/zpe"));
    }

    /** Returns whether given nodeType is a Docker host for infrastructure nodes */
    private static boolean isInfrastructureHost(NodeType nodeType) {
        return nodeType == NodeType.confighost ||
                nodeType == NodeType.proxyhost ||
                nodeType == NodeType.controllerhost;
    }

}

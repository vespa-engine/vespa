// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.google.common.net.InetAddresses;
import com.yahoo.collections.Pair;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SystemName;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerResources;
import com.yahoo.vespa.hosted.dockerapi.ContainerStats;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeMembership;
import com.yahoo.vespa.hosted.node.admin.nodeagent.ContainerData;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddresses;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddressesImpl;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
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

    private static final InetAddress IPV6_NPT_PREFIX = InetAddresses.forString("fd00::");
    private static final InetAddress IPV4_NPT_PREFIX = InetAddresses.forString("172.17.0.0");

    private final Docker docker;
    private final ProcessExecuter processExecuter;
    private final IPAddresses ipAddresses;

    public DockerOperationsImpl(Docker docker) {
        this(docker, new ProcessExecuter(), new IPAddressesImpl());
    }

    public DockerOperationsImpl(Docker docker, ProcessExecuter processExecuter, IPAddresses ipAddresses) {
        this.docker = docker;
        this.processExecuter = processExecuter;
        this.ipAddresses = ipAddresses;
    }

    @Override
    public void createContainer(NodeAgentContext context, ContainerData containerData, ContainerResources containerResources) {
        context.log(logger, "Creating container");

        Optional<Inet6Address> ipV6Address = ipAddresses.getIPv6Address(context.node().hostname());

        Docker.CreateContainerCommand command = docker.createContainerCommand(
                context.node().wantedDockerImage().get(), context.containerName())
                .withHostName(context.node().hostname())
                .withResources(containerResources)
                .withManagedBy(MANAGER_NAME)
                .withUlimit("nofile", 262_144, 262_144)
                // The nproc aka RLIMIT_NPROC resource limit works as follows:
                //  - A process has a (soft) nproc limit, either inherited by the parent or changed with setrlimit(2).
                //    In bash, a command's limit can be viewed and set with ulimit(1).
                //  - When a process forks, the number of processes on the host (across all containers) with
                //    the same real user ID is compared with the limit, and if above the limit, return EAGAIN.
                //
                // From experience our Vespa processes require a high limit, say 400k. For all other processes,
                // we would like to use a much lower limit, say 32k.
                .withUlimit("nproc", 409_600, 409_600)
                .withUlimit("core", -1, -1)
                .withAddCapability("SYS_PTRACE") // Needed for gcore, pstack etc.
                .withAddCapability("SYS_ADMIN")  // Needed for perf
                .withAddCapability("SYS_NICE");  // Needed for set_mempolicy to work

        if (context.node().membership().map(NodeMembership::clusterType).map("content"::equalsIgnoreCase).orElse(false)) {
            command.withSecurityOpts("seccomp=unconfined");
        }

        DockerNetworking networking = context.dockerNetworking();
        command.withNetworkMode(networking.getDockerNetworkMode());

        if (networking == DockerNetworking.NPT) {
            Optional<InetAddress> ipV6Local = ipV6Address.map(ip -> IPAddresses.prefixTranslate(ip, IPV6_NPT_PREFIX, 8));
            ipV6Local.ifPresent(command::withIpAddress);

            // IPv4 - Only present for some containers
            Optional<InetAddress> ipV4Local = ipAddresses.getIPv4Address(context.node().hostname())
                    .map(ipV4Address -> IPAddresses.prefixTranslate(ipV4Address, IPV4_NPT_PREFIX, 2));
            ipV4Local.ifPresent(command::withIpAddress);

            if (ipV4Local.isEmpty() && ipV6Address.isEmpty()) {
                throw new IllegalArgumentException("Container " + context.node().hostname() + " with " +
                                                   networking + " networking must have at least 1 IP address, " +
                                                   "but found none");
            }
            addEtcHosts(containerData, context.node().hostname(), ipV4Local, ipV6Local);
        }

        addMounts(context, command);

        logger.info("Creating new container with args: " + command);
        command.create();
    }

    void addEtcHosts(ContainerData containerData,
                     String hostname,
                     Optional<InetAddress> ipV4Local,
                     Optional<InetAddress> ipV6Local) {
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
                "ff02::2\tip6-allrouters\n");
        ipV6Local.ifPresent(ipv6 -> etcHosts.append(ipv6.getHostAddress()).append('\t').append(hostname).append('\n'));
        ipV4Local.ifPresent(ipv4 -> etcHosts.append(ipv4.getHostAddress()).append('\t').append(hostname).append('\n'));

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
    public void updateContainer(NodeAgentContext context, ContainerResources containerResources) {
        docker.updateContainer(context.containerName(), containerResources);
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
    public ProcessResult executeCommandInNetworkNamespace(NodeAgentContext context, String... command) {
        int containerPid = docker.getContainer(context.containerName())
                .filter(container -> container.state.isRunning())
                .orElseThrow(() -> new RuntimeException(
                        "Found no running container named " + context.containerName().asString()))
                .pid;

        String[] wrappedCommand = Stream.concat(Stream.of("nsenter",
                                                          String.format("--net=/proc/%d/ns/net", containerPid),
                                                          "--"),
                                                Stream.of(command))
                                        .toArray(String[]::new);

        try {
            Pair<Integer, String> result = processExecuter.exec(wrappedCommand);
            if (result.getFirst() != 0) {
                throw new RuntimeException(String.format(
                        "Failed to execute %s in network namespace for %s (PID = %d), exit code: %d, output: %s",
                        Arrays.toString(wrappedCommand), context.containerName().asString(), containerPid, result.getFirst(), result.getSecond()));
            }
            return new ProcessResult(0, result.getSecond(), "");
        } catch (IOException e) {
            throw new RuntimeException(String.format("IOException while executing %s in network namespace for %s (PID = %d)",
                    Arrays.toString(wrappedCommand), context.containerName().asString(), containerPid), e);
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

    private static void addMounts(NodeAgentContext context, Docker.CreateContainerCommand command) {
        Path varLibSia = Paths.get("/var/lib/sia");

        // Paths unique to each container
        List<Path> paths = new ArrayList<>(List.of(
                Paths.get("/etc/vespa/flags"), // local file db, to use flags before connection to cfg is established
                Paths.get("/etc/yamas-agent"), // metrics check configuration
                Paths.get("/opt/splunkforwarder/var/log"),  // VESPA-14917, thin pool leakage
                Paths.get("/var/log"),                      // VESPA-14917, thin pool leakage
                Paths.get("/var/spool/postfix/maildrop"),   // VESPA-14917, thin pool leakage
                context.pathInNodeUnderVespaHome("logs/daemontools_y"), // TODO: related to ykeykey?
                context.pathInNodeUnderVespaHome("logs/vespa"),
                context.pathInNodeUnderVespaHome("logs/yca"),
                context.pathInNodeUnderVespaHome("logs/ykeykeyd"), // TODO: should only be needed for proxy?
                context.pathInNodeUnderVespaHome("logs/ysar"),
                context.pathInNodeUnderVespaHome("tmp"),
                context.pathInNodeUnderVespaHome("var/crash"), // core dumps
                context.pathInNodeUnderVespaHome("var/container-data"),
                context.pathInNodeUnderVespaHome("var/db/vespa"),
                context.pathInNodeUnderVespaHome("var/jdisc_container"),
                context.pathInNodeUnderVespaHome("var/vespa"),
                context.pathInNodeUnderVespaHome("var/yca"),
                context.pathInNodeUnderVespaHome("var/zookeeper") // Tenant content nodes, config server and controller
        ));

        if (context.nodeType() == NodeType.proxy) {
            paths.add(context.pathInNodeUnderVespaHome("logs/nginx"));
            paths.add(context.pathInNodeUnderVespaHome("var/vespa-hosted/routing"));
        } else if (context.nodeType() == NodeType.tenant)
            paths.add(varLibSia);

        paths.forEach(path -> command.withVolume(context.pathOnHostFromPathInNode(path), path));


        // Shared paths
        if (isInfrastructureHost(context.nodeType()))
            command.withSharedVolume(varLibSia, varLibSia);

        boolean isMain = context.zone().getSystemName() == SystemName.cd || context.zone().getSystemName() == SystemName.main;
        if (isMain && context.nodeType() == NodeType.tenant)
            command.withSharedVolume(Paths.get("/var/zpe"), context.pathInNodeUnderVespaHome("var/zpe"));
    }

    @Override
    public boolean noManagedContainersRunning() {
        return docker.noManagedContainersRunning(MANAGER_NAME);
    }

    @Override
    public boolean deleteUnusedDockerImages(List<DockerImage> excludes, Duration minImageAgeToDelete) {
        return docker.deleteUnusedDockerImages(excludes, minImageAgeToDelete);
    }

    /** Returns whether given nodeType is a Docker host for infrastructure nodes */
    private static boolean isInfrastructureHost(NodeType nodeType) {
        return nodeType == NodeType.config ||
                nodeType == NodeType.proxy ||
                nodeType == NodeType.controller;
    }

}

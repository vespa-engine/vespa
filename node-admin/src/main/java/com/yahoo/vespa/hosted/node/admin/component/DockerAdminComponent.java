// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.net.HostName;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerClients;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdaterImpl;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.provider.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddressesImpl;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Component that manages Docker containers based on some node repository.
 */
public class DockerAdminComponent implements AdminComponent {
    private static final Duration NODE_AGENT_SCAN_INTERVAL = Duration.ofSeconds(30);
    private static final Duration NODE_ADMIN_CONVERGE_STATE_INTERVAL = Duration.ofSeconds(30);

    private final ConfigServerConfig configServerConfig;
    private final Docker docker;
    private final MetricReceiverWrapper metricReceiver;
    private final Optional<ClassLocking> classLocking;
    private final ConfigServerClients configServerClients;

    private Optional<Environment> environment = Optional.empty();
    private Optional<NodeAdminStateUpdaterImpl> nodeAdminStateUpdater = Optional.empty();

    public DockerAdminComponent(ConfigServerConfig configServerConfig,
                                Docker docker,
                                MetricReceiverWrapper metricReceiver,
                                ClassLocking classLocking,
                                ConfigServerClients configServerClients) {
        this(configServerConfig, docker, metricReceiver, Optional.empty(), Optional.of(classLocking), configServerClients);
    }

    public DockerAdminComponent(ConfigServerConfig configServerConfig,
                                Docker docker,
                                MetricReceiverWrapper metricReceiver,
                                Environment environment,
                                ConfigServerClients configServerClients) {
        this(configServerConfig, docker, metricReceiver, Optional.of(environment), Optional.empty(), configServerClients);
    }

    private DockerAdminComponent(ConfigServerConfig configServerConfig,
                                 Docker docker,
                                 MetricReceiverWrapper metricReceiver,
                                 Optional<Environment> environment,
                                 Optional<ClassLocking> classLocking,
                                 ConfigServerClients configServerClients) {
        this.configServerConfig = configServerConfig;
        this.docker = docker;
        this.metricReceiver = metricReceiver;
        this.environment = environment;
        this.classLocking = classLocking;
        this.configServerClients = configServerClients;
    }

    @Override
    public void enable() {
        if (nodeAdminStateUpdater.isPresent()) {
            return;
        }

        nodeAdminStateUpdater = Optional.of(createNodeAdminStateUpdater());
        nodeAdminStateUpdater.get().start();
    }

    private NodeAdminStateUpdaterImpl createNodeAdminStateUpdater() {
        if (!environment.isPresent()) {
            environment = Optional.of(new Environment(configServerConfig));
        }

        Clock clock = Clock.systemUTC();
        String dockerHostHostName = HostName.getLocalhost();
        ProcessExecuter processExecuter = new ProcessExecuter();

        docker.start();
        DockerOperations dockerOperations = new DockerOperationsImpl(
                docker,
                environment.get(),
                processExecuter,
                new IPAddressesImpl());

        StorageMaintainer storageMaintainer = new StorageMaintainer(
                dockerOperations,
                processExecuter,
                metricReceiver,
                environment.get(),
                clock);

        AclMaintainer aclMaintainer = new AclMaintainer(
                dockerOperations,
                configServerClients.nodeRepository(),
                dockerHostHostName);

        Function<String, NodeAgent> nodeAgentFactory = (hostName) -> new NodeAgentImpl(
                hostName,
                configServerClients.nodeRepository(),
                configServerClients.orchestrator(),
                dockerOperations,
                storageMaintainer,
                aclMaintainer,
                environment.get(),
                clock,
                NODE_AGENT_SCAN_INTERVAL);

        NodeAdmin nodeAdmin = new NodeAdminImpl(
                dockerOperations,
                nodeAgentFactory,
                storageMaintainer,
                aclMaintainer,
                metricReceiver,
                clock);

        return new NodeAdminStateUpdaterImpl(
                configServerClients.nodeRepository(),
                configServerClients.orchestrator(),
                storageMaintainer,
                nodeAdmin,
                dockerHostHostName,
                clock,
                NODE_ADMIN_CONVERGE_STATE_INTERVAL,
                classLocking);
    }

    @Override
    public void disable() {
        if (!nodeAdminStateUpdater.isPresent()) {
            return;
        }

        nodeAdminStateUpdater.ifPresent(NodeAdminStateUpdaterImpl::stop);
        configServerClients.stop();
        nodeAdminStateUpdater = Optional.empty();
    }

    @Override
    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return nodeAdminStateUpdater.get();
    }
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.component.AdminComponent;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.node.admin.component.DockerAdminComponent;
import com.yahoo.vespa.hosted.node.admin.provider.NodeAdminStateUpdater;

import java.io.File;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * NodeAdminMain is the main component of the node admin JDisc application:
 *  - It will read config and check its environment to figure out its responsibilities
 *  - It will "start" (only) the necessary components.
 *  - Other components MUST NOT try to start (typically in constructor) since the features
 *    they provide is NOT WANTED and possibly destructive, and/or the environment may be
 *    incompatible. For instance, trying to contact the Docker daemon too early will be
 *    fatal: the node admin may not have installed and started the docker daemon.
 */
public class NodeAdminMain implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(NodeAdminMain.class.getName());

    private final ComponentRegistry<AdminComponent> adminRegistry;
    private final ConfigServerConfig configServerConfig;
    private final Docker docker;
    private final MetricReceiverWrapper metricReceiver;
    private final ClassLocking classLocking;

    private AdminComponent mainAdminComponent = null;

    public NodeAdminMain(ComponentRegistry<AdminComponent> adminRegistry,
                         ConfigServerConfig configServerConfig,
                         Docker docker,
                         MetricReceiverWrapper metricReceiver,
                         ClassLocking classLocking) {
        this.adminRegistry = adminRegistry;
        this.configServerConfig = configServerConfig;
        this.docker = docker;
        this.metricReceiver = metricReceiver;
        this.classLocking = classLocking;
    }

    public static NodeAdminConfig getConfig() {
        return NodeAdminConfig.fromFile(new File("/etc/vespa/node-admin.json"));
    }

    public void start() {
        NodeAdminConfig config = getConfig();
        mainAdminComponent = selectAdminComponent(config);
        mainAdminComponent.enable();
    }

    private AdminComponent selectAdminComponent(NodeAdminConfig config) {
        if (config.mainComponent == null) {
            return new DockerAdminComponent(configServerConfig, docker, metricReceiver, classLocking);
        }

        logger.log(LogLevel.INFO, () -> {
            String registeredComponentsList = adminRegistry
                    .allComponentsById().keySet().stream()
                    .map(ComponentId::stringValue)
                    .collect(Collectors.joining(", "));

            return String.format(
                    "Components registered = '%s', enabled = '%s'",
                    registeredComponentsList,
                    config.mainComponent);
        });

        AdminComponent component = adminRegistry.getComponent(config.mainComponent);
        if (component == null) {
            throw new IllegalArgumentException("There is no component named '" +
                    config.mainComponent + "'");
        }

        return component;
    }

    @Override
    public void close() {
        if (mainAdminComponent != null) {
            mainAdminComponent.disable();
            mainAdminComponent = null;
        }
    }

    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        assert mainAdminComponent != null : "start() hasn't been called yet";
        return mainAdminComponent.getNodeAdminStateUpdater();
    }
}

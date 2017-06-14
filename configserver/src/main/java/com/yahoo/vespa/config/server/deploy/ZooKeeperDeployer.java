// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.provision.ProvisionInfo;
import com.yahoo.config.provision.Version;

import java.io.IOException;
import java.util.Map;

/**
 * Interface for initializing zookeeper and deploying an application package to zookeeper.
 * Initialize must be called before each deploy.
 *
 * @author lulf
 * @since 5.1
 */
public class ZooKeeperDeployer {

    private final ZooKeeperClient zooKeeperClient;

    public ZooKeeperDeployer(ZooKeeperClient client) {
        this.zooKeeperClient = client;
    }

    /**
     * Deploys an application package to zookeeper. initialize() must be called before calling this method.
     *
     * @param applicationPackage The application package to persist.
     * @param fileRegistryMap The file registries to persist.
     * @param provisionInfoMap The provisioning infos to persist.
     * @throws IOException if deploying fails
     */
    public void deploy(ApplicationPackage applicationPackage, Map<Version, FileRegistry> fileRegistryMap, Map<Version, ProvisionInfo> provisionInfoMap) throws IOException {
        zooKeeperClient.setupZooKeeper();
        zooKeeperClient.feedZooKeeper(applicationPackage);
        zooKeeperClient.feedZKFileRegistries(fileRegistryMap);
        zooKeeperClient.feedProvisionInfos(provisionInfoMap);
    }

    public void cleanup() {
        zooKeeperClient.cleanupZooKeeper();
    }
}

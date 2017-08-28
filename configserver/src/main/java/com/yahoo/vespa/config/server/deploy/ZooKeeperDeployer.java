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
     * @param fileRegistryMap the file registries to persist.
     * @param provisionInfo the provisioning info to persist.
     * @throws IOException if deploying fails
     */
    public void deploy(ApplicationPackage applicationPackage, Map<Version, FileRegistry> fileRegistryMap, 
                       ProvisionInfo provisionInfo) throws IOException {
        zooKeeperClient.setupZooKeeper();
        zooKeeperClient.feedZooKeeper(applicationPackage);
        zooKeeperClient.feedZKFileRegistries(fileRegistryMap);
        zooKeeperClient.feedProvisionInfo(provisionInfo);
    }

    public void cleanup() {
        zooKeeperClient.cleanupZooKeeper();
    }
}

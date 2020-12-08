// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;

import java.nio.file.Path;

/**
 * Main component controlling startup and stop of zookeeper server
 *
 * @author Ulf Lilleengen
 * @author hmusum
 */
public class VespaZooKeeperServerImpl extends AbstractComponent implements VespaZooKeeperServer {

    private final ZooKeeperRunner zooKeeperRunner;

    @Inject
    public VespaZooKeeperServerImpl(ZookeeperServerConfig zookeeperServerConfig) {
        this.zooKeeperRunner = new ZooKeeperRunner(zookeeperServerConfig, this);
    }

    @Override
    public void deconstruct() {
        zooKeeperRunner.shutdown();
        super.deconstruct();
    }

    public void start(Path configFilePath) {
        new ZooKeeperServer().start(configFilePath);
    }

}

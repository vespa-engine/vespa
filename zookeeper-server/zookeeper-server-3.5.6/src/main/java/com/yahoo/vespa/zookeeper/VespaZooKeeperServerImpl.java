// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;

/**
 * Main component controlling startup and stop of zookeeper server
 *
 * @author Ulf Lilleengen
 * @author Harald Musum
 */
public class VespaZooKeeperServerImpl extends AbstractComponent implements VespaZooKeeperServer {

    private final ZooKeeperRunner zooKeeperRunner;

    @Inject
    public VespaZooKeeperServerImpl(ZookeeperServerConfig zookeeperServerConfig) {
        this.zooKeeperRunner = new ZooKeeperRunner(zookeeperServerConfig);
    }

    @Override
    public void deconstruct() {
        zooKeeperRunner.shutdown();
        super.deconstruct();
    }

}

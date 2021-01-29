// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;

import java.nio.file.Path;

/**
 * @author Ulf Lilleengen
 * @author hmusum
 */
public class VespaZooKeeperServerImpl extends AbstractComponent implements VespaZooKeeperServer {

    private final VespaQuorumPeer peer;
    private final ZooKeeperRunner runner;

    @Inject
    public VespaZooKeeperServerImpl(ZookeeperServerConfig zookeeperServerConfig) {
        this.peer = new VespaQuorumPeer();
        this.runner = new ZooKeeperRunner(zookeeperServerConfig, this);
    }

    @Override
    public void deconstruct() {
        runner.shutdown();
        super.deconstruct();
    }

    @Override
    public void shutdown() {
        peer.shutdown();
    }

    @Override
    public void start(Path configFilePath) {
        peer.start(configFilePath);
    }

    @Override
    public boolean reconfigurable() {
        return false;
    }

}

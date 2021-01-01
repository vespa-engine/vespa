// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;

import java.nio.file.Path;

/**
 * Starts or reconfigures zookeeper cluster
 *
 * @author hmusum
 */
public class ReconfigurableVespaZooKeeperServer extends AbstractComponent implements VespaZooKeeperServer {

    private final VespaQuorumPeer peer;

    @Inject
    public ReconfigurableVespaZooKeeperServer(Reconfigurer reconfigurer, ZookeeperServerConfig zookeeperServerConfig) {
        this.peer = new VespaQuorumPeer();
        reconfigurer.startOrReconfigure(zookeeperServerConfig, this);
    }

    @Override
    public void shutdown() {
        peer.shutdown();
    }

    public void start(Path configFilePath) {
        peer.start(configFilePath);
    }

    @Override
    public boolean reconfigurable() {
        return true;
    }

}

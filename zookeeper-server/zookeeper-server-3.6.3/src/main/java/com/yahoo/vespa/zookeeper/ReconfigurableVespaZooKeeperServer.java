// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts or reconfigures zookeeper cluster.
 * The QuorumPeer conditionally created here is owned by the Reconfigurer;
 * when it already has a peer, that peer is used here in case start or shutdown is required.
 *
 * @author hmusum
 */
public class ReconfigurableVespaZooKeeperServer extends AbstractComponent implements VespaZooKeeperServer {

    private final AtomicReference<QuorumPeer> peer = new AtomicReference<>();

    @Inject
    public ReconfigurableVespaZooKeeperServer(Reconfigurer reconfigurer, ZookeeperServerConfig zookeeperServerConfig) {
        reconfigurer.startOrReconfigure(zookeeperServerConfig, this, VespaQuorumPeer::new, peer::set);
    }

    @Override
    public void shutdown() {
        peer.get().shutdown(Duration.ofMinutes(1));
    }

    @Override
    public void start(Path configFilePath) {
        peer.get().start(configFilePath);
    }

    @Override
    public boolean reconfigurable() {
        return true;
    }

}

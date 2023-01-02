// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import ai.vespa.validation.Validation;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Starts or reconfigures zookeeper cluster.
 * The QuorumPeer conditionally created here is owned by the Reconfigurer;
 * when it already has a peer, that peer is used here in case start or shutdown is required.
 *
 * @author hmusum
 */
public class ReconfigurableVespaZooKeeperServer extends AbstractComponent implements VespaZooKeeperServer {

    private QuorumPeer peer;

    @Inject
    public ReconfigurableVespaZooKeeperServer(Reconfigurer reconfigurer, ZookeeperServerConfig zookeeperServerConfig) {
        Validation.require(zookeeperServerConfig.dynamicReconfiguration(),
                           zookeeperServerConfig.dynamicReconfiguration(),
                           "dynamicReconfiguration must be true");
        peer = reconfigurer.startOrReconfigure(zookeeperServerConfig, this, () -> peer = new VespaQuorumPeer());
    }

    @Override
    public void shutdown() {
        peer.shutdown(Duration.ofMinutes(1));
    }

    @Override
    public void start(Path configFilePath) {
        peer.start(configFilePath);
    }

    @Override
    public boolean reconfigurable() {
        return true;
    }

}

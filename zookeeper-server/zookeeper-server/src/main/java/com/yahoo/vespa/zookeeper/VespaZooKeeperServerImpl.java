// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import ai.vespa.validation.Validation;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import java.nio.file.Path;
import java.time.Duration;

/**
 * ZooKeeper server. Guarantees that the server is up by writing a node to ZooKeeper successfully before
 * returning from constructor.
 *
 * @author Ulf Lilleengen
 * @author Harald Musum
 */
public class VespaZooKeeperServerImpl extends AbstractComponent implements VespaZooKeeperServer {

    private final VespaQuorumPeer peer;
    private final ZooKeeperRunner runner;

    @Inject
    public VespaZooKeeperServerImpl(ZookeeperServerConfig zookeeperServerConfig) {
        Validation.require(! zookeeperServerConfig.dynamicReconfiguration(),
                           ! zookeeperServerConfig.dynamicReconfiguration(),
                           "dynamicReconfiguration must be false");
        this.peer = new VespaQuorumPeer();
        this.runner = new ZooKeeperRunner(zookeeperServerConfig, this);
        new VespaZooKeeperAdminImpl().createDummyNode(zookeeperServerConfig);
    }

    @Override
    public void deconstruct() {
        runner.shutdown();
        super.deconstruct();
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
        return false;
    }

}

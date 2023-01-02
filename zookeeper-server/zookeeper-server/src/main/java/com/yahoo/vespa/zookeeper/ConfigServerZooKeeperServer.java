// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import java.nio.file.Path;

/**
 *
 * Server used for starting config server, needed to be able to have different behavior for hosted and
 * self-hosted Vespa (controlled by zookeeperServerConfig.dynamicReconfiguration).
 *
 * @author Harald Musum
 */
public class ConfigServerZooKeeperServer extends AbstractComponent implements VespaZooKeeperServer {

    private final VespaZooKeeperServer zooKeeperServer;

    @Inject
    public ConfigServerZooKeeperServer(ZookeeperServerConfig zookeeperServerConfig) {
        this.zooKeeperServer = zookeeperServerConfig.dynamicReconfiguration()
                ? new ReconfigurableVespaZooKeeperServer(new Reconfigurer(new VespaZooKeeperAdminImpl()), zookeeperServerConfig)
                : new VespaZooKeeperServerImpl(zookeeperServerConfig);
    }

    @Override
    public void deconstruct() { zooKeeperServer.shutdown(); }

    @Override
    public void shutdown() {
        zooKeeperServer.shutdown();
    }

    @Override
    public void start(Path configFilePath) {
        zooKeeperServer.start(configFilePath);
    }

    @Override
    public boolean reconfigurable() { return zooKeeperServer.reconfigurable(); }

}

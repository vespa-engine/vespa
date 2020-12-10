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

    @Inject
    public ReconfigurableVespaZooKeeperServer(Reconfigurer reconfigurer, ZookeeperServerConfig zookeeperServerConfig) {
        reconfigurer.startOrReconfigure(zookeeperServerConfig, this);
    }

    public void start(Path configFilePath) {
        new ZooKeeperServer().start(configFilePath);
    }

    public boolean reconfigurable() { return true; }

}

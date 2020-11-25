// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;

/**
 * Starts or reconfigures zookeeper cluster
 *
 * @author Harald Musum
 */
public class ReconfigurableVespaZooKeeperServer extends AbstractComponent implements VespaZooKeeperServer {

    @Inject
    public ReconfigurableVespaZooKeeperServer(Reconfigurer reconfigurer, ZookeeperServerConfig zookeeperServerConfig) {
        reconfigurer.startOrReconfigure(zookeeperServerConfig);
    }

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.yahoo.vespa.zookeeper.VespaZooKeeperServer;

/**
 * ZooKeeper provider that ensures we are running our own instance of zookeeper.
 *
 * @author Ulf Lilleengen
 */
public class StandaloneZooKeeperProvider implements ZooKeeperProvider {

    public StandaloneZooKeeperProvider(VespaZooKeeperServer server) {
    }

}

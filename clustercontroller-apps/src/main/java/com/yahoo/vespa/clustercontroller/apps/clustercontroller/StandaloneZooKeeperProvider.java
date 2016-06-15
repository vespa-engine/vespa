// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.yahoo.vespa.zookeeper.ZooKeeperServer;

/**
 * ZooKeeper provider that ensures we are running our own instance of zookeeper.
 *
 * @author lulf
 * @since 5.26
 */
public class StandaloneZooKeeperProvider implements ZooKeeperProvider {
    public StandaloneZooKeeperProvider(ZooKeeperServer server) {
    }
}

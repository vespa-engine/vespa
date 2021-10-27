// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import org.apache.curator.utils.ZookeeperFactory;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.client.ZKClientConfig;

/**
 * A ZooKeeper factory for creating a ZooKeeper client
 *
 * @author hmusum
 */
class VespaZooKeeperFactory implements ZookeeperFactory {

    private final ZKClientConfig zkClientConfig;

    VespaZooKeeperFactory(ZKClientConfig zkClientConfig) {
        this.zkClientConfig = zkClientConfig;
    }

    @Override
    public ZooKeeper newZooKeeper(String connectString, int sessionTimeout, Watcher watcher, boolean canBeReadOnly) throws Exception {
        return new ZooKeeper(connectString, sessionTimeout, watcher, zkClientConfig);
    }

}

// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import org.apache.curator.utils.ZookeeperFactory;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

/**
 * A ZooKeeper factory for creating a ZooKeeper client
 *
 * @author hmusum
 */
// TODO: add constructor that takes feature flag so that we can write ZooKeeper client config and start
// ZooKeeper client with that config
class VespaZooKeeperFactory implements ZookeeperFactory {

    @Override
    public ZooKeeper newZooKeeper(String connectString, int sessionTimeout, Watcher watcher, boolean canBeReadOnly) throws Exception {
        return new ZooKeeper(connectString, sessionTimeout, watcher);
    }

}

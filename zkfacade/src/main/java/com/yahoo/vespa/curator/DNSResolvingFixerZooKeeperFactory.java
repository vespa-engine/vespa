// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.log.LogLevel;
import org.apache.curator.utils.DefaultZookeeperFactory;
import org.apache.curator.utils.ZookeeperFactory;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A ZooKeeper handle factory that handles unknown host exceptions.
 *
 * @author lulf
 * @since 5.9
 */
class DNSResolvingFixerZooKeeperFactory implements ZookeeperFactory {

    public static final long UNKNOWN_HOST_WAIT_TIME_MILLIS = TimeUnit.SECONDS.toMillis(10);

    private static final Logger log = Logger.getLogger(DNSResolvingFixerZooKeeperFactory.class.getName());
    private final DefaultZookeeperFactory zookeeperFactory;
    private final long maxTimeout;
    public DNSResolvingFixerZooKeeperFactory(long maxTimeout) {
        this.maxTimeout = maxTimeout;
        this.zookeeperFactory = new DefaultZookeeperFactory();
    }
    @Override
    public ZooKeeper newZooKeeper(String connectString, int sessionTimeout, Watcher watcher, boolean canBeReadOnly) throws Exception {
        long endTime = System.currentTimeMillis() + maxTimeout;
        do {
            try {
                return zookeeperFactory.newZooKeeper(connectString, sessionTimeout, watcher, canBeReadOnly);
            } catch (UnknownHostException e) {
                log.log(LogLevel.WARNING, "Error creating ZooKeeper handle", e);
                Thread.sleep(UNKNOWN_HOST_WAIT_TIME_MILLIS);
            }
        } while (System.currentTimeMillis() < endTime);
        throw new RuntimeException("Error creating zookeeper handle within timeout");
    }

}

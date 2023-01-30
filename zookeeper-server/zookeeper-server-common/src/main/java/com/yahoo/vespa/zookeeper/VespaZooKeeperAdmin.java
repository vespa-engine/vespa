// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.net.HostName;
import java.time.Duration;

/**
 * Interface for administering a zookeeper cluster. Currently only supports reconfiguring a zookeeper cluster.
 *
 * @author hmusum
 */
public interface VespaZooKeeperAdmin {

    void reconfigure(String connectionSpec, String servers) throws ReconfigException;

    /* Timeout for connecting to ZooKeeper */
    default Duration sessionTimeout() { return Duration.ofSeconds(30); }

    default String localConnectionSpec(ZookeeperServerConfig config) { return HostName.getLocalhost() + ":" + config.clientPort(); }

}

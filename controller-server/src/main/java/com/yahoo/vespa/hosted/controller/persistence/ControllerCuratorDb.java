// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.net.HostName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;

import java.util.stream.Collectors;

/**
 * A CuratorDb that configures its own ZooKeeper cluster
 *
 * @author bratseth
 */
// TODO: Remove when multi controller is enabled
@Deprecated
public class ControllerCuratorDb extends CuratorDb {

    /** Use a nonstandard zk port to avoid interfering with connection to the config server zk cluster */
    private static final int zooKeeperPort = 2281;

    @SuppressWarnings("unused") // This server is used (only) from the curator instance of this over the network */
    private final ZooKeeperServer zooKeeperServer;

    /** Create a curator db which also set up a ZooKeeper server (such that this instance is both client and server) */
    @Inject
    public ControllerCuratorDb(ClusterInfoConfig clusterInfo) {
        super(new Curator(toConnectionSpec(clusterInfo)));
        this.zooKeeperServer = new ZooKeeperServer(toZookeeperServerConfig(clusterInfo));
    }

    private static ZookeeperServerConfig toZookeeperServerConfig(ClusterInfoConfig clusterInfo) {
        ZookeeperServerConfig.Builder b = new ZookeeperServerConfig.Builder();
        b.zooKeeperConfigFile("conf/zookeeper/controller-zookeeper.cfg");
        b.dataDir("var/controller-zookeeper");
        b.clientPort(zooKeeperPort);
        b.myidFile("var/controller-zookeeper/myid");
        b.myid(myIndex(clusterInfo));

        for (ClusterInfoConfig.Services clusterMember : clusterInfo.services()) {
            ZookeeperServerConfig.Server.Builder server = new ZookeeperServerConfig.Server.Builder();
            server.id(clusterMember.index());
            server.hostname(clusterMember.hostname());
            server.quorumPort(zooKeeperPort + 1);
            server.electionPort(zooKeeperPort + 2);
            b.server(server);
        }
        return new ZookeeperServerConfig(b);
    }

    private static Integer myIndex(ClusterInfoConfig clusterInfo) {
        String hostname = HostName.getLocalhost();
        return clusterInfo.services().stream()
                          .filter(service -> service.hostname().equals(hostname))
                          .map(ClusterInfoConfig.Services::index)
                          .findFirst()
                          .orElseThrow(() -> new IllegalStateException("Unable to find index for this node by hostname '" +
                                                                       hostname + "'"));
    }

    private static String toConnectionSpec(ClusterInfoConfig clusterInfo) {
        return clusterInfo.services().stream()
                          .map(member -> member.hostname() + ":" + zooKeeperPort)
                          .collect(Collectors.joining(","));
    }
}

// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import org.apache.zookeeper.server.admin.AdminServer;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.apache.zookeeper.server.quorum.QuorumPeerMain;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Class to start zookeeper server. Extends QuorumPeerMain to be able to call initializeAndRun() and wraps
 * exceptions so it can be used by code that does not depend on zookeeper.
 *
 * @author hmusum
 */
class ZooKeeperServer extends QuorumPeerMain {

    public void start(Path path) {
        initializeAndRun(new String[]{ path.toFile().getAbsolutePath()});
    }

    @Override
    protected void initializeAndRun(String[] args) {
        try {
            super.initializeAndRun(args);
        } catch (QuorumPeerConfig.ConfigException | IOException | AdminServer.AdminServerException e) {
            throw new RuntimeException("Exception when initializing or running ZooKeeper server", e);
        }
    }

}

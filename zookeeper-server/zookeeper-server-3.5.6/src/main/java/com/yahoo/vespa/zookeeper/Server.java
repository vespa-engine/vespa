package com.yahoo.vespa.zookeeper;

import org.apache.zookeeper.server.admin.AdminServer;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.apache.zookeeper.server.quorum.QuorumPeerMain;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Extends QuorumPeerMain to be able to call initializeAndRun()
 */
class Server extends QuorumPeerMain {

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

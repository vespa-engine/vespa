// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.protect.Process;
import org.apache.zookeeper.server.admin.AdminServer;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.apache.zookeeper.server.quorum.QuorumPeerMain;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Starts/stops a ZooKeeper server. Extends QuorumPeerMain to be able to call initializeAndRun() and wraps
 * exceptions so it can be used by code that does not depend on ZooKeeper.
 *
 * @author hmusum
 */
class VespaQuorumPeer extends QuorumPeerMain {

    public void start(Path path) {
        initializeAndRun(new String[]{ path.toFile().getAbsolutePath()});
    }

    public void shutdown() {
        if (quorumPeer != null) {
            try {
                quorumPeer.shutdown();
            } catch (RuntimeException e) {
                // If shutdown fails, we have no other option than forcing the JVM to stop and letting it be restarted.
                //
                // When a VespaZooKeeperServer component receives a new config, the container will try to start a new
                // server with the new config, this will fail until the old server is deconstructed. If the old server
                // fails to deconstruct/shut down, the new one will never start and if that happens forcing a restart is
                // the better option.
                Process.logAndDie("Failed to shut down ZooKeeper properly, forcing shutdown", e);
            }
        }
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

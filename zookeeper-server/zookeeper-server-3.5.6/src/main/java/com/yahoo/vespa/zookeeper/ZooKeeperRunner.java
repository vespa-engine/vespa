// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.apache.zookeeper.server.admin.AdminServer;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.apache.zookeeper.server.quorum.QuorumPeerMain;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static com.yahoo.vespa.zookeeper.Configurator.zookeeperServerHostnames;

/**
 * Writes zookeeper config and starts zookeeper server.
 *
 * @author Harald Musum
 */
public class ZooKeeperRunner implements Runnable {
    private static final Logger log = java.util.logging.Logger.getLogger(ZooKeeperRunner.class.getName());

    private final ExecutorService executorService;
    private final ZookeeperServerConfig zookeeperServerConfig;

    public ZooKeeperRunner(ZookeeperServerConfig zookeeperServerConfig) {
        this.zookeeperServerConfig = zookeeperServerConfig;
        new Configurator(zookeeperServerConfig).writeConfigToDisk(TransportSecurityUtils.getOptions());
        executorService = Executors.newSingleThreadExecutor(new DaemonThreadFactory("zookeeper server"));
        executorService.submit(this);
    }

    void shutdown() {
        executorService.shutdownNow();
    }

    @Override
    public void run() {
        String[] args = new String[]{getDefaults().underVespaHome(zookeeperServerConfig.zooKeeperConfigFile())};
        log.log(Level.INFO, "Starting ZooKeeper server with config file " + args[0] +
                            ". Trying to establish ZooKeeper quorum (members: " + zookeeperServerHostnames(zookeeperServerConfig) + ")");
        new Server().initializeAndRun(args);
    }

    /**
     * Extends QuoroumPeerMain to be able to call initializeAndRun()
     */
    private static class Server extends QuorumPeerMain {

        @Override
        protected void initializeAndRun(String[] args) {
            try {
                super.initializeAndRun(args);
            } catch (QuorumPeerConfig.ConfigException | IOException | AdminServer.AdminServerException e) {
                throw new RuntimeException("Exception when initializing or running ZooKeeper server", e);
            }
        }

    }

}

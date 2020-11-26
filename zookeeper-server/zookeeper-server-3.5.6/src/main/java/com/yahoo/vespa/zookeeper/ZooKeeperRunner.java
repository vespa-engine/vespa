// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.security.tls.TransportSecurityUtils;

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

    private final Thread zkServerThread;
    private final ZookeeperServerConfig zookeeperServerConfig;

    public ZooKeeperRunner(ZookeeperServerConfig zookeeperServerConfig) {
        this.zookeeperServerConfig = zookeeperServerConfig;
        new Configurator(zookeeperServerConfig).writeConfigToDisk(TransportSecurityUtils.getOptions());
        zkServerThread = new Thread(this, "zookeeper server");
        zkServerThread.start();
    }

    void shutdown() {
        zkServerThread.interrupt();
        try {
            zkServerThread.join();
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Error joining server thread on shutdown", e);
        }
    }

    @Override
    public void run() {
        String[] args = new String[]{getDefaults().underVespaHome(zookeeperServerConfig.zooKeeperConfigFile())};
        log.log(Level.INFO, "Starting ZooKeeper server with config file " + args[0] +
                            ". Trying to establish ZooKeeper quorum (members: " + zookeeperServerHostnames(zookeeperServerConfig) + ")");
        org.apache.zookeeper.server.quorum.QuorumPeerMain.main(args);
    }

    public ZookeeperServerConfig zookeeperServerConfig() {
        return zookeeperServerConfig;
    }

}

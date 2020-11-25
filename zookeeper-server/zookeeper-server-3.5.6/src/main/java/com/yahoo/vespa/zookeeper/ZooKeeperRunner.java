// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.security.tls.TransportSecurityUtils;

import java.util.logging.Level;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static com.yahoo.vespa.zookeeper.Configurator.zookeeperServerHostnames;

/**
 * Writes zookeeper config and starts zookeeper server.
 *
 * @author Ulf Lilleengen
 * @author Harald Musum
 */
public class VespaZooKeeperServerImpl extends AbstractComponent implements Runnable, VespaZooKeeperServer {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(VespaZooKeeperServerImpl.class.getName());
    private final Thread zkServerThread;
    private final ZookeeperServerConfig zookeeperServerConfig;

    @Inject
    public VespaZooKeeperServerImpl(ZookeeperServerConfig zookeeperServerConfig) {
        this.zookeeperServerConfig = zookeeperServerConfig;
        new Configurator(zookeeperServerConfig).writeConfigToDisk(TransportSecurityUtils.getOptions());
        zkServerThread = new Thread(this, "zookeeper server");
        zkServerThread.start();
    }

    private void shutdown() {
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

    @Override
    public void deconstruct() {
        shutdown();
        super.deconstruct();
    }

}

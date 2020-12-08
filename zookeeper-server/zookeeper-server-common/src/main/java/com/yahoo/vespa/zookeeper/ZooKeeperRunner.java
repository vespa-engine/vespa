// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.security.tls.TransportSecurityUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private final VespaZooKeeperServer server;

    public ZooKeeperRunner(ZookeeperServerConfig zookeeperServerConfig, VespaZooKeeperServer server) {
        this.zookeeperServerConfig = zookeeperServerConfig;
        this.server = server;
        new Configurator(zookeeperServerConfig).writeConfigToDisk(TransportSecurityUtils.getOptions());
        executorService = Executors.newSingleThreadExecutor(new DaemonThreadFactory("zookeeper server"));
        executorService.submit(this);
    }

    void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10000, TimeUnit.MILLISECONDS)) {
                log.log(Level.WARNING, "Failed to shut down within timeout");
            }
        } catch (InterruptedException e) {
            log.log(Level.INFO, "Interrupted waiting for executor to complete", e);
        }
        if ( ! executorService.isTerminated()) {
            executorService.shutdownNow();
        }
    }

    @Override
    public void run() {
        Path path = Paths.get(getDefaults().underVespaHome(zookeeperServerConfig.zooKeeperConfigFile()));
        log.log(Level.INFO, "Starting ZooKeeper server with config file " + path.toFile().getAbsolutePath() +
                            ". Trying to establish ZooKeeper quorum (members: " + zookeeperServerHostnames(zookeeperServerConfig) + ")");
        server.start(path);
    }

}

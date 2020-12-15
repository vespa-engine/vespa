// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.security.tls.TransportSecurityUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
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
    private static final Duration shutdownTimeout = Duration.ofSeconds(10);

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
        log.log(Level.INFO, "Triggering shutdown");
        executorService.shutdownNow();
        log.log(Level.INFO, "Shutdown triggered");
        try {
            if (!executorService.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.log(Level.WARNING, "Failed to shut down within " + shutdownTimeout);
            }
        } catch (InterruptedException e) {
            log.log(Level.INFO, "Interrupted waiting for executor to complete", e);
        }
    }

    @Override
    public void run() {
        Path path = Paths.get(getDefaults().underVespaHome(zookeeperServerConfig.zooKeeperConfigFile()));

        // Retry start of server. An already running server might take some time to shut down, starting a new
        // one will fail in that case, so retry
        Instant end = Instant.now().plus(Duration.ofMinutes(10));
        do {
            try {
                log.log(Level.INFO, "Starting ZooKeeper server with config file " + path.toFile().getAbsolutePath() +
                                    ". Trying to establish ZooKeeper quorum (members: " + zookeeperServerHostnames(zookeeperServerConfig) + ")");
                server.start(path); // Will block in a real implementation of VespaZooKeeperServer
                return;
            } catch (RuntimeException e) {
                log.log(Level.INFO, "Starting ZooKeeper server failed, will retry", e);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException interruptedException) {
                    log.log(Level.INFO, "Failed interrupting task", e);
                }
            }
        } while (Instant.now().isBefore(end) && !executorService.isShutdown());
    }

}

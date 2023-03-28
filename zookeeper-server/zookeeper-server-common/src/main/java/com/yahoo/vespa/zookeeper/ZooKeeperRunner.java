// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.protect.Process;
import com.yahoo.yolean.Exceptions;

import java.nio.file.Files;
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
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration START_TIMEOUT = Duration.ofMinutes(10);

    private final ExecutorService executorService;
    private final ZookeeperServerConfig zookeeperServerConfig;
    private final VespaZooKeeperServer server;
    private final ExponentialBackoff backoff = new ExponentialBackoff(Duration.ofSeconds(5), Duration.ofSeconds(15));
    private final Sleeper sleeper = new Sleeper();

    public ZooKeeperRunner(ZookeeperServerConfig zookeeperServerConfig, VespaZooKeeperServer server) {
        this.zookeeperServerConfig = zookeeperServerConfig;
        this.server = server;
        new Configurator(zookeeperServerConfig).writeConfigToDisk();
        executorService = Executors.newSingleThreadExecutor(new DaemonThreadFactory("zookeeper-server-"));
        executorService.submit(this);
    }

    void shutdown() {
        log.log(Level.INFO, "Triggering shutdown");
        server.shutdown();
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.log(Level.WARNING, "Failed to shut down within " + STOP_TIMEOUT);
            }
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Interrupted waiting for executor to complete", e);
        }
    }

    @Override
    public void run() {
        Path path = Paths.get(getDefaults().underVespaHome(zookeeperServerConfig.zooKeeperConfigFile()));

        // Retry start of server. An already running server might take some time to shut down, starting a new
        // one will fail in that case, so retry
        Instant now = Instant.now();
        Instant end = now.plus(START_TIMEOUT);
        for (int attempt = 1; now.isBefore(end) && !executorService.isShutdown(); attempt++) {
            try {
                startServer(path, attempt); // Will block in a real implementation of VespaZooKeeperServer
                return;
            } catch (RuntimeException e) {
                String messagePart = "Starting " + serverDescription() + " failed on attempt " + attempt;
                if (server.reconfigurable()) {
                    Duration delay = backoff.delay(attempt);
                    log.log(Level.WARNING, messagePart + ". Retrying in " + delay + ", time left " +
                                           Duration.between(now, end), e);
                    sleeper.sleep(delay);
                } else {
                    Process.logAndDie(messagePart + ". Forcing shutdown", e);
                }
            } finally {
                now = Instant.now();
            }
        }
        // Failed, log config
        log.log(Level.INFO, "Current content of zookeeper config file at '" + path + "':\n" +
                Exceptions.uncheck(() -> Files.readString(path)));
    }

    private String serverDescription() {
        return (server.reconfigurable() ? "" : "non-") + "reconfigurable ZooKeeper server";
    }

    private void startServer(Path path, int attempt) {
        if (attempt > 1)
            log.log(Level.INFO, "Starting ZooKeeper server with " + path.toFile().getAbsolutePath() +
                    ". Trying to establish ZooKeeper quorum (members: " +
                    zookeeperServerHostnames(zookeeperServerConfig) + ", attempt " + attempt + ")");

        // Note: Hack to make this work in ZooKeeper 3.6, where metrics provider class is
        // loaded by using Thread.currentThread().getContextClassLoader() which does not work
        // well in the container
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        try {
            server.start(path);
        } catch (Throwable e) {
            throw new RuntimeException("Starting " + serverDescription() + " failed", e);
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

}

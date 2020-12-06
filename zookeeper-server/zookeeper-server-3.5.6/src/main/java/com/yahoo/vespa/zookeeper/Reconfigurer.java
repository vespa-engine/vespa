// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.yolean.Exceptions;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.admin.ZooKeeperAdmin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Starts zookeeper server and supports reconfiguring zookeeper cluster. Created as a component
 * without any config injected, to make sure that it is not recreated when config changes.
 *
 * @author hmusum
 */
public class Reconfigurer extends AbstractComponent {

    private static final Logger log = java.util.logging.Logger.getLogger(Reconfigurer.class.getName());

    // Timeout for connecting to ZooKeeper to reconfigure
    private static final Duration sessionTimeout = Duration.ofSeconds(30);

    // How long to wait before triggering reconfig. This is multiplied by the node ID
    private static final Duration reconfigInterval = Duration.ofSeconds(5);

    // Total timeout for a reconfiguration
    private static final Duration reconfigTimeout = Duration.ofSeconds(30);

    // How long to wait between each retry
    private static final Duration retryWait = Duration.ofSeconds(1);

    private ZooKeeperRunner zooKeeperRunner;
    private ZookeeperServerConfig activeConfig;

    @Inject
    public Reconfigurer() {
        log.log(Level.FINE, "Created ZooKeeperReconfigurer");
    }

    void startOrReconfigure(ZookeeperServerConfig newConfig) {
        startOrReconfigure(newConfig, Reconfigurer::defaultSleeper);
    }

    void startOrReconfigure(ZookeeperServerConfig newConfig, Consumer<Duration> sleeper) {
        if (zooKeeperRunner == null)
            zooKeeperRunner = startServer(newConfig);

        if (shouldReconfigure(newConfig))
            reconfigure(newConfig, sleeper);
    }

    ZookeeperServerConfig activeConfig() {
        return activeConfig;
    }

    void zooKeeperReconfigure(String connectionSpec, String joiningServers, String leavingServers) throws KeeperException {
        try {
            ZooKeeperAdmin zooKeeperAdmin = new ZooKeeperAdmin(connectionSpec,
                                                               (int) sessionTimeout.toMillis(),
                                                               new LoggingWatcher());
            long fromConfig = -1;
            // Using string parameters because the List variant of reconfigure fails to join empty lists (observed on 3.5.6, fixed in 3.7.0)
            byte[] appliedConfig = zooKeeperAdmin.reconfigure(joiningServers, leavingServers, null, fromConfig, null);
            log.log(Level.INFO, "Applied ZooKeeper config: " + new String(appliedConfig, StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    void shutdown() {
        if (zooKeeperRunner != null) {
            zooKeeperRunner.shutdown();
        }
    }

    private boolean shouldReconfigure(ZookeeperServerConfig newConfig) {
        if (!newConfig.dynamicReconfiguration()) return false;
        if (activeConfig == null) return false;
        return !newConfig.equals(activeConfig());
    }

    private ZooKeeperRunner startServer(ZookeeperServerConfig zookeeperServerConfig) {
        ZooKeeperRunner runner = new ZooKeeperRunner(zookeeperServerConfig);
        activeConfig = zookeeperServerConfig;
        return runner;
    }

    private void reconfigure(ZookeeperServerConfig newConfig, Consumer<Duration> sleeper) {
        Instant reconfigTriggered = Instant.now();
        String leavingServers = String.join(",", difference(serverIds(activeConfig), serverIds(newConfig)));
        String joiningServers = String.join(",", difference(servers(newConfig), servers(activeConfig)));
        leavingServers = leavingServers.isEmpty() ? null : leavingServers;
        joiningServers = joiningServers.isEmpty() ? null : joiningServers;
        log.log(Level.INFO, "Will reconfigure ZooKeeper cluster in " + reconfigWaitPeriod() +
                            ". Joining servers: " + joiningServers + ", leaving servers: " + leavingServers);
        sleeper.accept(reconfigWaitPeriod());
        String connectionSpec = connectionSpec(activeConfig);
        boolean reconfigured = false;
        Instant end = Instant.now().plus(reconfigTimeout);
        // Loop reconfiguring since we might need to wait until another reconfiguration is finished before we can succeed
        for (int attempts = 1; ! reconfigured && Instant.now().isBefore(end); attempts++) {
            try {
                Instant reconfigStarted = Instant.now();
                zooKeeperReconfigure(connectionSpec, joiningServers, leavingServers);
                Instant reconfigEnded = Instant.now();
                log.log(Level.INFO, "Reconfiguration completed in " +
                                    Duration.between(reconfigTriggered, reconfigEnded) +
                                    ", after " + attempts + " attempt(s). ZooKeeper reconfig call took " +
                                    Duration.between(reconfigStarted, reconfigEnded));
                reconfigured = true;
            } catch (KeeperException e) {
                if (!retryOn(e))
                    throw new RuntimeException(e);
                log.log(Level.INFO, "Reconfiguration failed. Retrying in " + retryWait + ": " +
                                    Exceptions.toMessageString(e));
                sleeper.accept(retryWait);
            }
        }
        activeConfig = newConfig;
    }

    /** Returns how long this node should wait before reconfiguring the cluster */
    private Duration reconfigWaitPeriod() {
        if (activeConfig == null) return Duration.ZERO;
        return reconfigInterval.multipliedBy(activeConfig.myid());
    }

    private static boolean retryOn(KeeperException e) {
        return e instanceof KeeperException.ReconfigInProgress ||
               e instanceof  KeeperException.ConnectionLossException;
    }

    private static String connectionSpec(ZookeeperServerConfig config) {
        return config.server().stream()
                     .map(server -> server.hostname() + ":" + config.clientPort())
                     .collect(Collectors.joining(","));
    }

    private static List<String> serverIds(ZookeeperServerConfig config) {
        return config.server().stream()
                     .map(ZookeeperServerConfig.Server::id)
                     .map(String::valueOf)
                     .collect(Collectors.toList());
    }

    private static List<String> servers(ZookeeperServerConfig config) {
        // See https://zookeeper.apache.org/doc/r3.5.8/zookeeperReconfig.html#sc_reconfig_clientport for format
        return config.server().stream()
                     .map(server -> server.id() + "=" + server.hostname() + ":" + server.quorumPort() + ":" +
                                    server.electionPort() + ";" + config.clientPort())
                     .collect(Collectors.toList());
    }

    private static <T> List<T> difference(List<T> list1, List<T> list2) {
        List<T> copy = new ArrayList<>(list1);
        copy.removeAll(list2);
        return copy;
    }

    private static void defaultSleeper(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
        }
    }

    private static class LoggingWatcher implements Watcher {

        @Override
        public void process(WatchedEvent event) {
            log.log(Level.INFO, event.toString());
        }

    }

}
